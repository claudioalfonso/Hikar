package freemap.hikar;

import freemap.andromaps.DialogUtils;
import freemap.data.Point;
import freemap.data.Projection;
import freemap.proj.Proj4ProjectionFactory;

import android.hardware.GeomagneticField;
import android.hardware.SensorManager;
import android.location.Location;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.ViewGroup.LayoutParams;
import android.view.MenuItem;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.location.LocationManager;

import java.lang.ref.WeakReference;


public class Hikar extends Activity implements SensorInput.SensorInputReceiver,
        LocationProcessor.Receiver,DownloadDataTask.Receiver,
        PinchListener.Handler
{
    LocationProcessor locationProcessor;
    HUD hud;

    // from viewfrag
    OsmDemIntegrator integrator;
    OpenGLView glView;
    DownloadDataTask downloadDataTask;
    SensorInput sensorInput;
    String tilingProjID;
    Point locDisplayProj;
    int demType;
    String[] tilingProjIDs = { "epsg:27700", "epsg:4326" };
    TileDisplayProjectionTransformation trans;
    String lfpUrl, srtmUrl, osmUrl;
    GeomagneticField field;
    float orientationAdjustment;
    double lastLon, lastLat;
    boolean receivedLocation;
    OpenGLViewStatusHandler openGLViewStatusHandler;

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
            if(activity != null) {
                if (data.containsKey("hfov")) {
                    float hfov = data.getFloat("hfov");
                    activity.hud.setHFOV(hfov);
                    activity.hud.invalidate();
                } else if (data.containsKey("finishedData") &&
                        data.getBoolean("finishedData")==true) {
                    activity.hud.removeMessage();
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openGLViewStatusHandler = new OpenGLViewStatusHandler(this);
        glView = new OpenGLView(this, openGLViewStatusHandler);
        hud=new HUD(this);
        setContentView(glView);
        addContentView(hud, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

        sensorInput = new SensorInput(this);
        sensorInput.attach(this);
        locationProcessor = new LocationProcessor(this,this,5000,10);
        glView.setOnTouchListener(new PinchListener(this));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if(prefs != null) {
            float orientationAdjustment = prefs.getFloat("orientationAdjustment", 0.0f);
            changeOrientationAdjustment(orientationAdjustment);
            hud.changeOrientationAdjustment(orientationAdjustment);
        }


        tilingProjID = "";

        demType = OsmDemIntegrator.HGT_OSGB_LFP;
        trans = new TileDisplayProjectionTransformation ( null, null );
        lfpUrl = "http://www.free-map.org.uk/downloads/lfp/";
        srtmUrl = "http://www.free-map.org.uk/ws/";
        osmUrl = "http://www.free-map.org.uk/fm/ws/";
        lastLon = -181;
        lastLat = -91;

    }
    
    public void onPause() {
        super.onPause();
       locationProcessor.stopUpdates();
        sensorInput.stop();
        glView.getRenderer().onPause();
    }

    public void onResume() {
        super.onResume();
        locationProcessor.startUpdates();
        processPrefs();
        glView.getRenderer().onResume();
        sensorInput.start();
    }

    public void onDestroy() {
        super.onDestroy();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("orientationAdjustment", getOrientationAdjustment());
        editor.commit();

        sensorInput.detach();
    }

    public void processPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        float cameraHeight = Float.parseFloat(prefs.getString("prefCameraHeight","1.4"));
        setCameraHeight(cameraHeight);
        String prefSrtmUrl=prefs.getString("prefSrtmUrl","http://www.free-map.org.uk/ws/"),
                prefLfpUrl=prefs.getString("prefLfpUrl", "http://www.free-map.org.uk/downloads/lfp/"),
                prefOsmUrl=prefs.getString("prefOsmUrl", "http://www.free-map.org.uk/fm/ws/");
        boolean urlchange = setDataUrls(prefLfpUrl, prefSrtmUrl, prefOsmUrl);
        int prefDEM = Integer.valueOf(prefs.getString("prefDEM","0"));
        String oldTilingProjID = tilingProjID;
        int oldDemType = demType;
        setDEM(prefDEM);
        String prefDisplayProjectionID = "epsg:" + prefs.getString("prefDisplayProjection", "27700");
        if(!setDisplayProjectionID(prefDisplayProjectionID))
            DialogUtils.showDialog(this, "Invalid projection " + prefDisplayProjectionID);
        else {
            Proj4ProjectionFactory fac = new Proj4ProjectionFactory();
            trans.setTilingProj(fac.generate(tilingProjID));

            if (integrator == null || !tilingProjID.equals(oldTilingProjID) ||
                    oldDemType != demType || urlchange) {
                integrator = new OsmDemIntegrator(trans.getTilingProj(), demType, lfpUrl, srtmUrl, osmUrl);

                // If we received a location but weren't activated, now load data from the last location
                if (receivedLocation) {
                    setLocation(lastLon, lastLat, true);
                }
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
        boolean retcode=false;

        switch(item.getItemId())
        {

            case R.id.menu_settings:
                Intent i = new Intent(this,Preferences.class);
                startActivity(i);
                break;


            case R.id.menu_location:
                LocationManager mgr = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

                if(!mgr.isProviderEnabled(LocationManager.GPS_PROVIDER))
                {
                    Intent intent = new Intent(this, LocationEntryActivity.class);
                    startActivityForResult (intent, 0);
                }
                else
                {
                    DialogUtils.showDialog(this, "Can only manually specify location when GPS is off");
                }
                break;

        }
        return retcode;
    }

    public void onActivityResult (int requestCode, int resultCode, Intent intent) {
        if(resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case 0:
                    Bundle info = intent.getExtras();
                    double lon = info.getDouble("freemap.hikar.lon"), lat = info.getDouble("freemap.hikar.lat");
                    android.util.Log.d("hikar", "setting locaton to " + lon + "," + lat);
                    setLocation(lon, lat);
                    break;
            }
        }
    }
    
    public boolean onKeyDown(int key, KeyEvent ev) {
       
        boolean handled=false;
        
        switch(key) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                changeOrientationAdjustment(-1.0f);
                hud.changeOrientationAdjustment(-1.0f);
                handled=true;
                break;
                
            case KeyEvent.KEYCODE_VOLUME_UP:
                changeOrientationAdjustment(1.0f);
                hud.changeOrientationAdjustment(1.0f);
                handled=true;
                break;
        }
       
        return handled ? true: super.onKeyDown(key, ev);
    }
    
    public boolean onKeyUp(int key, KeyEvent ev) {
        return key==KeyEvent.KEYCODE_VOLUME_DOWN || key==KeyEvent.KEYCODE_VOLUME_UP ? true: super.onKeyUp(key,ev);
    }


    public void receiveLocation(Location loc) {
        setLocation(loc.getLongitude(),loc.getLatitude(), true);
    }

    public void setLocation(double lon, double lat)
    {
        setLocation (lon, lat, false);
    }

    public void setLocation(double lon, double lat, boolean gpsLocation) {
        if(gpsLocation) {
            receivedLocation=true;
            lastLon = lon;
            lastLat = lat;
        }

        if(integrator!=null) {

            Point p = new Point(lon, lat);
            double height = integrator.getHeight(p);
            p.z = height;

            // We assume we won't travel far enough in one session for magnetic north to change much
            if(field==null) {
                field = new GeomagneticField ((float)lat, (float)lon,
                        0, System.currentTimeMillis());
            }

            locDisplayProj = trans.getDisplayProj().project(p);

            Log.d("hikar","location in display projection=" + locDisplayProj);
            glView.getRenderer().setCameraLocation(p);

            hud.setHeight((float)height);
            hud.invalidate();

            if(integrator.needNewData(p) && downloadDataTask==null) {
                Log.d("hikar", "Starting download data task");
                downloadDataTask = new DownloadDataTask(this, this,
                        hud, integrator, gpsLocation);
                downloadDataTask.execute(p);
            } else if (!integrator.needNewData(p)) {
                Log.d("hikar", "We don't need new data");
            }
        }
    }

    public void noGPS() { }

    public void receiveData(DownloadDataTask.ReceivedData data, boolean sourceGPS) {
        Log.d("hikar", "received data: sourceGPS=" + sourceGPS);
        if (data!=null){ // only show data if it's a gps location, not a manual entry
            Log.d("hikar", "data not null");
            if(sourceGPS) {
                Log.d("hikar", "Calling setRenderData()");
                glView.getRenderer().setRenderData(data);
            }
        }

        downloadDataTask = null;

        // 180215 Now the DEM has been loaded we can get an initial height (as long as we have a location)
        if(receivedLocation) {
            double height = integrator.getHeight(new Point(lastLon, lastLat));
            hud.setHeight((float)height);
            hud.invalidate();
            glView.getRenderer().setHeight(height);
        }
    }

    public void receiveSensorInput(float[] glR) {
        float[] orientation = new float[3];

        float magNorth = field==null ? 0.0f : field.getDeclination(),
                actualAdjustment = magNorth + orientationAdjustment;
        Matrix.rotateM (glR, 0, actualAdjustment, 0.0f, 0.0f, 1.0f);

        SensorManager.getOrientation(glR, orientation);

        glView.getRenderer().setOrientMtx(glR);
        hud.setOrientation(orientation);
        hud.invalidate();
    }

    public void onPinchIn() {
        glView.getRenderer().changeHFOV(5.0f);

    }

    public void onPinchOut() {
        glView.getRenderer().changeHFOV(-5.0f);

    }

    public void setHFOV(float hFov) {

        hud.setHFOV(hFov);
        hud.invalidate();
    }


    public void setCameraHeight(float cameraHeight) {
        android.util.Log.d("hikar","camera height=" + cameraHeight);
        glView.getRenderer().setCameraHeight(cameraHeight);
    }


    public boolean setDEM (int demType) {

        this.demType = demType;
        if(!(tilingProjID.equals(tilingProjIDs[demType]))) {
            tilingProjID = tilingProjIDs[demType];
            return true;
        }

        return false;
    }

    public boolean setDisplayProjectionID (String displayProjectionID) {
        Proj4ProjectionFactory fac = new Proj4ProjectionFactory();
        Projection proj = fac.generate(displayProjectionID);
        if(proj!=null) {
            trans.setDisplayProj(proj);
            glView.getRenderer().setProjectionTransformation (trans);
            return true;
        }
        return false;
    }

    public boolean setDataUrls (String lfpUrl, String srtmUrl, String osmUrl) {
        boolean change=!(this.lfpUrl.equals(lfpUrl)) || !(this.srtmUrl.equals(srtmUrl)) || !(this.osmUrl.equals(osmUrl));
        this.lfpUrl = lfpUrl;
        this.srtmUrl = srtmUrl;
        this.osmUrl = osmUrl;
        return change;
    }

    public void changeOrientationAdjustment(float amount)
    {
        orientationAdjustment += amount;
    }

    public float getOrientationAdjustment()
    {
        return orientationAdjustment;
    }
}
