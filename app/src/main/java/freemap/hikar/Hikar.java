package freemap.hikar;

import freemap.andromaps.ConfigChangeSafeTask;
import freemap.andromaps.DialogUtils;
import freemap.data.Point;
import freemap.data.Projection;
import freemap.data.Way;
import freemap.datasource.OSMTiles;
import freemap.proj.Proj4ProjectionFactory;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.GeomagneticField;
import android.hardware.SensorManager;
import android.location.Location;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.ViewGroup.LayoutParams;
import android.view.MenuItem;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.WindowManager;

import freemap.routing.CountyManager;
import freemap.routing.CountyTracker;
import freemap.routing.JunctionManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;


public class Hikar extends AppCompatActivity implements SensorInput.SensorInputReceiver,
        LocationProcessor.Receiver, DownloadDataTask.Receiver,
        PinchListener.Handler {
    LocationProcessor locationProcessor;
    HUD hud;
    boolean userHasSelectedMode;

    // from viewfrag
    OsmDemIntegrator integrator;
    OpenGLView glView;
    DownloadDataTask downloadDataTask;
    SensorInput sensorInput;
    String tilingProjID;
    Point locDisplayProj;
    int demType;
    String[] tilingProjIDs = {"epsg:27700", "epsg:4326"};
    TileDisplayProjectionTransformation trans;
    String lfpUrl, srtmUrl, osmUrl;
    GeomagneticField field;
    float orientationAdjustment;
    double lastLon, lastLat;
    boolean receivedLocation;
    OpenGLViewStatusHandler openGLViewStatusHandler;
    boolean enableOrientationAdjustment;

    JunctionManager jManager;
    SignpostManager sManager;
    CountyManager cManager;

    CountyTracker cTracker;
    boolean loadingCounty;

    static String DEFAULT_LFP_URL = "http://www.free-map.org.uk/downloads/lfp/",
            DEFAULT_SRTM_URL = "http://www.free-map.org.uk/ws/srtm2.php",
            DEFAULT_OSM_URL = "http://www.free-map.org.uk/fm/ws/bsvr2.php";

    // end

    // http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
    static class OpenGLViewStatusHandler extends Handler {
        WeakReference<Hikar> activityRef;

        public OpenGLViewStatusHandler(Hikar activity) {
            activityRef = new WeakReference<Hikar>(activity);
        }

        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            Hikar activity = activityRef.get();
            if (activity != null) {
               if (data.containsKey("finishedData") &&
                        data.getBoolean("finishedData") == true) {
                    activity.hud.removeMessage();
                } else if (data.containsKey("hfov")) {
                   activity.hud.setHFOV(data.getFloat("hfov"));
               }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openGLViewStatusHandler = new OpenGLViewStatusHandler(this);
        glView = new OpenGLView(this, openGLViewStatusHandler);
        hud = new HUD(this);
        setContentView(glView);
        addContentView(hud, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        sensorInput = new SensorInput(this);
        sensorInput.attach(this);
        locationProcessor = new LocationProcessor(this, this, 5000, 10);
        glView.setOnTouchListener(new PinchListener(this));

        tilingProjID = "";

        demType = OsmDemIntegrator.HGT_OSGB_LFP;
        trans = new TileDisplayProjectionTransformation(null, null);
        lfpUrl = DEFAULT_LFP_URL;
        srtmUrl = DEFAULT_SRTM_URL;
        osmUrl = DEFAULT_OSM_URL;
        lastLon = -181;
        lastLat = -91;

        // setLocation(-0.72, 51.05, true);


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs != null) {
            orientationAdjustment = prefs.getFloat("orientationAdjustment", 0.0f);
            hud.changeOrientationAdjustment(orientationAdjustment);
        }
        Intent intent = new Intent(this, ModeSelector.class);
        startActivityForResult(intent, 0);

        jManager = new JunctionManager(20);
        cManager = new CountyManager(Environment.getExternalStorageDirectory().getAbsolutePath()+
                "/hikar/countyData");

        countyInit();
    }

    public void onPause() {
        super.onPause();
        if (userHasSelectedMode) {
            locationProcessor.stopUpdates();
            sensorInput.stop();
            glView.getRenderer().onPause();
        }
    }

    public void onResume() {
        super.onResume();
        if (userHasSelectedMode) {
            locationProcessor.startUpdates();
            processPrefs();
            glView.getRenderer().onResume();
            sensorInput.start();
            //     setLocation(-0.72, 51.05, true);
        }
    }

    public void onDestroy() {
        super.onDestroy();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("orientationAdjustment", orientationAdjustment);
        editor.commit();

        sensorInput.detach();
    }

    public void processPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        float cameraHeight = Float.parseFloat(prefs.getString("prefCameraHeight", "1.4"));
        setCameraHeight(cameraHeight);

        enableOrientationAdjustment = prefs.getBoolean("prefEnableOrientationAdjustment", false);
        hud.setOrientationAdjustmentEnabled(enableOrientationAdjustment);

        String prefSrtmUrl = prefs.getString("prefSrtmUrl", DEFAULT_SRTM_URL),
                prefLfpUrl = prefs.getString("prefLfpUrl", DEFAULT_LFP_URL),
                prefOsmUrl = prefs.getString("prefOsmUrl", DEFAULT_OSM_URL);
        boolean urlchange = setDataUrls(prefLfpUrl, prefSrtmUrl, prefOsmUrl);

        if (integrator == null || urlchange) {
            integrator = new OsmDemIntegrator(trans.getTilingProj(), demType, lfpUrl, srtmUrl, osmUrl);
            if(downloadDataTask != null) {
                downloadDataTask.deactivate(); // will prevent processing of any data currently being downloaded
            }
            glView.getRenderer().deactivate(); // remove any rendered data which might be from another data source

            // If we received a location but weren't activated, now load data from the last location
            if (receivedLocation) {
                setLocation(lastLon, lastLat, true);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }


    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retcode = false;


        switch (item.getItemId()) {

            case R.id.menu_settings:
                Intent i = new Intent(this, Preferences.class);
                startActivity(i);
                break;


            case R.id.menu_location:

                if (downloadDataTask != null && downloadDataTask.getStatus() != AsyncTask.Status.FINISHED) {
                    DialogUtils.showDialog(this, "Cannot manually download data while an automatic download "+
                            "is in progress. Please wait until the download has completed.");
                } else{
                    if (locationProcessor.isProviderEnabled()) {
                        locationProcessor.stopUpdates();
                        new AlertDialog.Builder(this).setMessage("GPS updates will be stopped while " +
                            "data is being downloaded and resumed afterwards.").
                            setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface i, int which) {
                                    startManualDownload();
                                }
                            }).show();
                    } else {
                        startManualDownload();
                    }
                }

                break;

            case R.id.userguide:
                Intent intent = new Intent(this, UserGuide.class);
                startActivity(intent);
                break;

            case R.id.about:
                DialogUtils.showDialog(this, "Hikar v0.2-SNAPSHOT, (c) Nick Whitelegg 2013-18. " +
                            "Licensed under the GPL, v3. Uses OpenStreetMap data, " +
                            "copyright 2004-18 OpenStreetMap contributors, Open Database License. " +
                            "Also uses Ordnance Survey LandForm PANORAMA height data, Crown Copyright.");
                break;

                /*
            case R.id.fakegps:
                setLocation(-0.72, 51.05, true);
                break;
                */

        }
        return retcode;
    }

    public void startManualDownload() {
        Intent intent = new Intent(this, LocationEntryActivity.class);
        startActivityForResult(intent, 1);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) {
            Bundle info = null;
            switch (requestCode) {
                case 0:
                    info = intent.getExtras();
                    int mode = info.getInt("freemap.hikar.mode");
                    String displayProjection = info.getString("freemap.hikar.displayProjection");
                    setDEMSourceAndProjections(mode, displayProjection);
                    userHasSelectedMode = true;
                    break;
                case 1:
                    info = intent.getExtras();
                    double lon = info.getDouble("freemap.hikar.lon"), lat = info.getDouble("freemap.hikar.lat");
                    if(!validLocation(lon,lat)) {
                        DialogUtils.showDialog(this, "Invalid longitude and latitude. "+
                                (tilingProjID.equals("epsg:27700") ? "If using OSGB as projection, "+
                            "longitude must be between 7W and 2E and latitude between 49N and 59N.": ""));
                    } else {
                        setLocation(lon, lat);
                    }
                    break;
            }
        } else if (resultCode == Activity.RESULT_CANCELED && requestCode == 0) {
            finish();
        }
    }

    public void setDEMSourceAndProjections(int mode, String displayProjection) {

        demType = mode;
        tilingProjID = tilingProjIDs[demType];
        String displayProjectionFullId = "epsg:" + displayProjection;
        if (!setDisplayProjectionID(displayProjectionFullId))
            DialogUtils.showDialog(this, "Invalid projection " + displayProjectionFullId);
        else {
            Proj4ProjectionFactory fac = new Proj4ProjectionFactory();
            trans.setTilingProj(fac.generate(tilingProjID));
        }
    }

    public boolean onKeyDown(int key, KeyEvent ev) {

        boolean handled = false;
        if (enableOrientationAdjustment) {


            switch (key) {
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    orientationAdjustment -= 1.0f;
                    hud.changeOrientationAdjustment(-1.0f);
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_VOLUME_UP:
                    orientationAdjustment += 1.0f;
                    hud.changeOrientationAdjustment(1.0f);
                    handled = true;
                    break;
            }
        }

        return handled ? true : super.onKeyDown(key, ev);
    }

    public boolean onKeyUp(int key, KeyEvent ev) {
        return key == KeyEvent.KEYCODE_VOLUME_DOWN || key == KeyEvent.KEYCODE_VOLUME_UP ? true : super.onKeyUp(key, ev);
    }


    public void receiveLocation(Location loc) {
        setLocation(loc.getLongitude(), loc.getLatitude(), true);
    }

    public void setLocation(double lon, double lat) {
        setLocation(lon, lat, false);
    }

    public void setLocation(double lon, double lat, boolean gpsLocation) {
        if (gpsLocation) {
            receivedLocation = true;
            lastLon = lon;
            lastLat = lat;
        }

        if (integrator != null && validLocation(lon, lat)) {

            Point p = new Point(lon, lat);
            double height = integrator.getHeight(p);
            p.z = height;

            // We assume we won't travel far enough in one session for magnetic north to change much
            if (field == null) {
                field = new GeomagneticField((float) lat, (float) lon,
                        0, System.currentTimeMillis());
            }

            locDisplayProj = trans.getDisplayProj().project(p);

            glView.getRenderer().setCameraLocation(p);

            hud.setHeight((float) height);
            hud.invalidate();

            if (downloadDataTask == null || downloadDataTask.getStatus() == AsyncTask.Status.FINISHED) {
                if (integrator.needNewData(p)) {
                    downloadDataTask = new DownloadDataTask(this, this,
                            hud, integrator, gpsLocation);
                    downloadDataTask.execute(p);
                } else if (integrator.checkForFailedData(p) && gpsLocation) {
                    downloadDataTask = new FixFailedTilesTask(this, this, hud, integrator);
                    downloadDataTask.execute(p);
                }
            }

            signpostUpdate(p);
        }
    }



    public void noGPS() {
    }

    public void receiveData(DownloadDataTask.ReceivedData data, boolean sourceGPS) {
        if (data != null) { // only show data if it's a gps location, not a manual entry

            if (sourceGPS) {
                glView.getRenderer().setRenderData(data);
                setSignpostDataset(data);
            } else {
                locationProcessor.startUpdates();
                hud.removeMessage();
            }
        }

        downloadDataTask = null;

        // 180215 Now the DEM has been loaded we can get an initial height (as long as we have a location)
        if (receivedLocation) {
            double height = integrator.getHeight(new Point(lastLon, lastLat));
            hud.setHeight((float) height);
            hud.invalidate();
            glView.getRenderer().setHeight(height);
        }
    }

    public void receiveSensorInput(float[] glR) {
        float[] orientation = new float[3];

        float magNorth = field == null ? 0.0f : field.getDeclination(),
                actualAdjustment = magNorth + (enableOrientationAdjustment ? orientationAdjustment : 0);
        Matrix.rotateM(glR, 0, actualAdjustment, 0.0f, 0.0f, 1.0f);

        SensorManager.getOrientation(glR, orientation);

        glView.getRenderer().setOrientMtx(glR);
        hud.setOrientation(orientation);
        hud.invalidate();
    }

    public void onPinchIn() {
        glView.getRenderer().changeHFOV(5.0f);
        hud.changeHFOV(5.0f);
        hud.invalidate();
    }

    public void onPinchOut() {
        glView.getRenderer().changeHFOV(-5.0f);
        hud.changeHFOV(-5.0f);
        hud.invalidate();
    }


    public void setCameraHeight(float cameraHeight) {
        glView.getRenderer().setCameraHeight(cameraHeight);
    }


    public boolean setDisplayProjectionID(String displayProjectionID) {
        Proj4ProjectionFactory fac = new Proj4ProjectionFactory();

        Projection proj = fac.generate(displayProjectionID);
        if (proj != null) {
            trans.setDisplayProj(proj);
            glView.getRenderer().setProjectionTransformation(trans);
            return true;
        }
        return false;
    }

    public boolean setDataUrls(String lfpUrl, String srtmUrl, String osmUrl) {
        boolean change = !(this.lfpUrl.equals(lfpUrl)) || !(this.srtmUrl.equals(srtmUrl)) || !(this.osmUrl.equals(osmUrl));
        this.lfpUrl = lfpUrl;
        this.srtmUrl = srtmUrl;
        this.osmUrl = osmUrl;
        return change;
    }

    private boolean validLocation(double lon, double lat) {
        return !(lon>180 || lon<-180 || lat>90 || lat<-90 || (tilingProjID.equals("epsg:27700") &&
            lon<-7 || lon>2 || lat<49 || lat>59));
    }

    // Signpost stuff begins here ...

    private void indirectSetText(String heading, String msg) {
        // TODO
    }

    private void showIndirectText() {
        // TODO
    }


    private void countyInit() {
        sManager = new SignpostManager(this);
        ConfigChangeSafeTask<Void, Void> countyLoaderTask = new ConfigChangeSafeTask<Void, Void>(this)
        {
            public String doInBackground(Void... unused)
            {
                try
                {
                    cManager.downloadOrLoad("http://download.geofabrik.de/europe/great-britain/england/");
                    return "OK";
                }
                catch(IOException e)
                {
                    return e.toString();
                }
            }

            public void onPostExecute(String result)
            {
                super.onPostExecute(result);
                if(result.equals("OK"))
                {
                    cTracker = new CountyTracker(cManager);
                    cTracker.addCountyChangeListener(sManager);
                }
            }
        };
        countyLoaderTask.setDialogDetails("Loading...", "Loading county data...");
        countyLoaderTask.execute();
    }

    private void signpostUpdate(Point p) {
        new AsyncTask<Point, Void, Point>() {
            public Point doInBackground(Point... pt) {
                Point junction = null;

                try
                {
                    loadingCounty = true;


                    if (jManager.hasDataset()) {
                        junction = jManager.getJunction(pt[0]);
                        if (junction != null && sManager.hasDataset()) {
                            sManager.onJunction(junction);
                        }
                        else
                            indirectSetText("Can't call onJunction()", "Junction: " +
                                    junction + " sManager has dataset?" + sManager.hasDataset());

                    }
                    else
                    {
                        indirectSetText("Can't call onJunction()", "jManager.hasDataset() returned false");
                    }
                }
                catch(Exception e) {indirectSetText("Exception: ", "ViewFragment/Junction AsyncTask" +
                        e.toString()); }
                return junction;
            }

            public void onPostExecute(Point junction) {
                showIndirectText();
                if (junction != null) {
                    ArrayList<Way> jWays = jManager.getStoredWays();
                    String details = "";
                    for (int i = 0; i < jWays.size(); i++) {
                        details +=
                                (i == 0 ? "" : ",") + (jWays.get(i).getValue("name") == null ?
                                        jWays.get(i).getId() : jWays.get(i).getValue("name"))
                                        + "(" + jWays.get(i).getValue("highway") + ")";
                    }

                    DialogUtils.showDialog(Hikar.this, junction.toString() + ":" + details +
                            " onJunction() call time: "+ sManager.callTime);
                }

                loadingCounty = false;
            }
        }.execute(p);

        // CountyTracker will be null if error loading the counties or not loaded yet
        // This shouldn't go in an AsyncTask as it creates one itself to load the graph
        if (cTracker != null)
        {
            cTracker.update(p);
        }
    }

    private void setSignpostDataset(DownloadDataTask.ReceivedData data) {
        jManager.setDataset(new OSMTiles(data.osm));
        sManager.setDataset(new OSMTiles(data.osm));
    }
}
