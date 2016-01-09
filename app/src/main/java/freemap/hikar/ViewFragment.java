package freemap.hikar;

import freemap.andromaps.DialogUtils;
import freemap.data.Point;
import freemap.proj.Proj4ProjectionFactory;
import android.app.Activity;
import android.app.Fragment;
import android.hardware.SensorManager;
import android.location.Location;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.util.Log;
import android.hardware.GeomagneticField;
import android.os.Handler;
import android.os.Message;

import java.util.HashMap;
import freemap.datasource.Tile;
import freemap.data.Projection;

import java.lang.ref.WeakReference;


import android.widget.TextView;

/* 090116 imports related to signposts - comment out for the moment
import java.util.ArrayList;
import freemap.data.Way;
import java.io.IOException;
import freemap.routing.CountyManager;
import freemap.routing.CountyTracker;
import freemap.routing.JunctionManager;
import freemap.andromaps.ConfigChangeSafeTask;
import freemap.datasource.OSMTiles;
import android.os.Environment;F
import android.os.AsyncTask;
*/



public class ViewFragment extends Fragment
        implements LocationProcessor.Receiver,DownloadDataTask.Receiver,
        OpenGLView.RenderedWayVisitor,
        SensorInput.SensorInputReceiver, PinchListener.Handler
{

    // http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
    static class HFOVHandler extends Handler
    {
        WeakReference<HUDProvider> hudProvider;

        public HFOVHandler(HUDProvider p)
        {
            hudProvider = new WeakReference<HUDProvider>(p);
        }

        public void handleMessage(Message msg)
        {
            Bundle data = msg.getData();
            float hfov = data.getFloat("hfov");
            if (hudProvider.get() != null)
            {
                hudProvider.get().getHUD().setHFOV(hfov);
                hudProvider.get().getHUD().invalidate();
            }
        }
    }

    OsmDemIntegrator integrator;
    OpenGLView glView;
    DownloadDataTask downloadDataTask;
    LocationProcessor locationProcessor;
    SensorInput sensorInput;
    String tilingProjID;
    Point locDisplayProj;
    long lineOfSightTestFinish;
    boolean doingLineOfSightTest, activated;
    int demType;
    String[] tilingProjIDs = { "epsg:27700", "epsg:4326" };
    TileDisplayProjectionTransformation trans;
    String lfpUrl, srtmUrl, osmUrl;
    GeomagneticField field;
    float orientationAdjustment;
    double lastLon, lastLat;
    boolean receivedLocation;
    HFOVHandler hfovHandler;

    /* 090116 comment out for the moment
    JunctionManager jManager;
    SignpostManager sManager;
    CountyManager cManager;

    CountyTracker cTracker;
    boolean loadingCounty;
    */
    String curHeading, curDetails;


    public interface HUDProvider
    {
        public HUD getHUD();
    }



    public ViewFragment()
    {
        tilingProjID = "";
        setRetainInstance(true);
        sensorInput = new SensorInput(this);
        demType = OsmDemIntegrator.HGT_OSGB_LFP;
        trans = new TileDisplayProjectionTransformation ( null, null );
        lfpUrl = "http://www.free-map.org.uk/downloads/lfp/";
        srtmUrl = "http://www.free-map.org.uk/ws/";
        osmUrl = "http://www.free-map.org.uk/fm/ws/";
        lastLon = -181;
        lastLat = -91;

        /* 090116 comment out for the moment
        jManager = new JunctionManager(20);
        cManager = new CountyManager(Environment.getExternalStorageDirectory().getAbsolutePath()+
                                        "/hikar/countyData");
        */

        curHeading = "";
        curDetails = "";

    }

    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        hfovHandler = new HFOVHandler((Hikar)activity);
        // We don't do any reprojection on RenderedWays if the display projection and tiling projection
        // are the same

        glView = new OpenGLView(activity, hfovHandler);
        sensorInput.attach(activity);
        locationProcessor = new LocationProcessor(activity,this,5000,10);
        glView.setOnTouchListener(new PinchListener(this));

        if(integrator!=null)
        {
            HashMap<String, Tile> data = integrator.getCurrentOSMTiles();
            HashMap<String, Tile> dem = integrator.getCurrentDEMTiles();

            if(data!=null && dem!=null) {
                glView.getRenderer().setRenderData(new DownloadDataTask.ReceivedData(data, dem));
            }
        }

    }

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

    }

    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState)
    {
        // for routing/signposts
        //return inflater.inflate(R.layout.routetester, parent);
        return glView;
    }

    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        /* 090116 out for the moment
        sManager = new SignpostManager(getActivity(), this);

        ConfigChangeSafeTask<Void, Void> countyLoaderTask = new ConfigChangeSafeTask<Void, Void>(getActivity())
        {
            public String doInBackground(Void... unused)
            {
                try
                {

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
        */
    }

    public void onResume() {
        super.onResume();
        locationProcessor.startUpdates();
        glView.getRenderer().onResume();

        sensorInput.start();
    }

    public void onPause() {
        super.onPause();
        locationProcessor.stopUpdates();
        sensorInput.stop();
        glView.getRenderer().onPause();
    }

    public void onDetach() {
        super.onDetach();
        sensorInput.detach();
    }

    public void setActivate (boolean activate)
    {
        if(activate)
        {
            Proj4ProjectionFactory fac = new Proj4ProjectionFactory();
            trans.setTilingProj(fac.generate(tilingProjID));
            integrator = new OsmDemIntegrator(trans.getTilingProj(), demType, lfpUrl, srtmUrl, osmUrl);


            // If we received a location but weren't activated, now load data from the last location
            if(receivedLocation)
                setLocation(lastLon, lastLat, true);
        }
        else
        {
            integrator = null;
            glView.getRenderer().deactivate();
        }
        activated = activate;
    }

    public void receiveLocation(Location loc)
    {

        setLocation(loc.getLongitude(),loc.getLatitude(), true);
    }

    public void setLocation(double lon, double lat)
    {
        setLocation (lon, lat, false);
    }

    private void setLocation(double lon, double lat, boolean gpsLocation)
    {
        if(gpsLocation)
        {
            receivedLocation=true;
            lastLon = lon;
            lastLat = lat;
        }

        if(integrator!=null)
        {

            Point p = new Point(lon, lat);
            double height = integrator.getHeight(p);
            p.z = height;

            // We assume we won't travel far enough in one session for magnetic north to change much
            if(field==null)
            {
                field = new GeomagneticField ((float)lat, (float)lon,
                        0, System.currentTimeMillis());
            }

            locDisplayProj = trans.getDisplayProj().project(p);

            Log.d("hikar","location in display projection=" + locDisplayProj);
            glView.getRenderer().setCameraLocation(p);

            ((HUDProvider)getActivity()).getHUD().setHeight((float)height);
            ((HUDProvider)getActivity()).getHUD().invalidate();

            if(integrator.needNewData(p) && downloadDataTask==null)
            {
                downloadDataTask = new DownloadDataTask(this.getActivity(), this, integrator, gpsLocation);
                downloadDataTask.setDialogDetails("Loading...", "Loading data...");
                downloadDataTask.setShowDialogOnFinish(true);
                downloadDataTask.execute(p);
            }

            /* 090116 comment out for the moment
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

                            DialogUtils.showDialog(getActivity(), junction.toString() + ":" + details +
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
             */
        }
    }

    public void noGPS() { }

    public void receiveData(DownloadDataTask.ReceivedData data, boolean sourceGPS)
    {
        Log.d("hikar", "received data");
        DialogUtils.showDialog(getActivity(), "Data received");

        if (data!=null && sourceGPS) // only show data if it's a gps location, not a manual entry
        {
            glView.getRenderer().setRenderData(data);
            /* 090116 comment out for the moment
            jManager.setDataset(new OSMTiles(data.osm));
            sManager.setDataset(new OSMTiles(data.osm));
            */
        }
        else if (data==null)
            DialogUtils.showDialog(this.getActivity(), "Warning - received data is null!");
        else if (!sourceGPS)
            DialogUtils.showDialog(this.getActivity(), "Notice - sourceGPS is false");

        downloadDataTask = null;

        // 180215 Now the DEM has been loaded we can get an initial height (as long as we have a location)
        if(receivedLocation)
        {
            double height = integrator.getHeight(new Point(lastLon, lastLat));
            ((HUDProvider)getActivity()).getHUD().setHeight((float)height);
            ((HUDProvider)getActivity()).getHUD().invalidate();
             glView.getRenderer().setHeight(height);
        }
    }

    public void receiveSensorInput(float[] glR)
    {
        float[] orientation = new float[3];

        float magNorth = field==null ? 0.0f : field.getDeclination(),
                actualAdjustment = magNorth + orientationAdjustment;
        Matrix.rotateM(glR, 0, actualAdjustment, 0.0f, 0.0f, 1.0f);

        SensorManager.getOrientation(glR, orientation);

        glView.getRenderer().setOrientMtx(glR);
        ((HUDProvider)getActivity()).getHUD().setOrientation(orientation);
        ((HUDProvider)getActivity()).getHUD().invalidate();
    }

    public void onPinchIn()
    {
        glView.getRenderer().changeHFOV(5.0f);

    }

    public void onPinchOut()
    {
       glView.getRenderer().changeHFOV(-5.0f);
    }

    public void setHFOV(float hFov)
    {
        if (getActivity() != null)
        {
            ((HUDProvider)getActivity()).getHUD().setHFOV(hFov);
            ((HUDProvider)getActivity()).getHUD().invalidate();
        }
    }

    public void toggleCalibrate()
    {

        glView.getRenderer().toggleCalibrate();
    }

    public void setCameraHeight(float cameraHeight)
    {
        android.util.Log.d("hikar","camera height=" + cameraHeight);
        glView.getRenderer().setCameraHeight(cameraHeight);
    }

    public void visit(RenderedWay rw)
    {
        // old line of sight stuff - removed
    }

    public boolean setDEM (int demType)
    {

        this.demType = demType;
        if(!(tilingProjID.equals(tilingProjIDs[demType])))
        {
            tilingProjID = tilingProjIDs[demType];
            return true;
        }

        return false;
    }

    public boolean setDisplayProjectionID (String displayProjectionID)
    {
        Proj4ProjectionFactory fac = new Proj4ProjectionFactory();
        Projection proj = fac.generate(displayProjectionID);
        if(proj!=null)
        {

            trans.setDisplayProj(proj);
            glView.getRenderer().setProjectionTransformation (trans);
            return true;
        }
        return false;
    }

    public boolean setDataUrls (String lfpUrl, String srtmUrl, String osmUrl)
    {
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

    public void setText (String heading, String details)
    {
        ((TextView)getActivity().findViewById(R.id.heading)).setText(heading);
        ((TextView)getActivity().findViewById(R.id.content)).setText(details);
    }


    public void indirectSetText (String heading, String details)
    {
        curHeading = heading;
        curDetails = details +"@" + System.currentTimeMillis()/1000;
    }

    public void showIndirectText()
    {
            setText(curHeading, curDetails +", showIndirectText() called@" + System.currentTimeMillis()/1000);


    }
}
