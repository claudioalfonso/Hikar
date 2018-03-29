package freemap.hikar;

// The overall manager of the whole signposting functionality.
// Note that SignpostManager manages individual signposts.

import android.os.AsyncTask;
import android.os.Environment;

import java.io.IOException;
import java.util.ArrayList;

import freemap.andromaps.ConfigChangeSafeTask;
import freemap.andromaps.DialogUtils;
import freemap.data.Point;
import freemap.data.Way;
import freemap.datasource.OSMTiles;
import freemap.routing.CountyManager;
import freemap.routing.CountyTracker;
import freemap.routing.JunctionManager;
import freemap.routing.RegionInfo;

public class SignpostingManager {

    JunctionManager jManager;
    SignpostManager sManager;
    CountyManager cManager;

    CountyTracker cTracker;
    boolean loadingCounty, initialised;

    Hikar activity;

    public SignpostingManager(Hikar activity) {
        this.activity = activity;

        jManager = new JunctionManager(20);
        cManager = new CountyManager(Environment.getExternalStorageDirectory().getAbsolutePath()+
                "/gh/countyData");

        sManager = new SignpostManager(activity, activity);
    }



    // initialises the signposting by:
    // - downloading or loading the relevant county polygons (hard coded at the moment)
    // - instantiating the CountyTracker (which needs the polygons to work)
    // - setting the signpost manager as the county change listener

    public void initialise() {


        ConfigChangeSafeTask<Void, Void> countyLoaderTask = new ConfigChangeSafeTask<Void, Void>(activity)
        {
            public String doInBackground(Void... unused)
            {
                try
                {
                    RegionInfo info = new RegionInfo("europe", "great-britain", "england", null);
                    cManager.downloadOrLoad(info, new String[] { "hampshire", "west-sussex", "wiltshire", "surrey"});
                    return "Polys downloaded OK";
                }
                catch(IOException e)
                {
                    return e.toString();
                }
            }

            public void onPostExecute(String result)
            {
                super.onPostExecute(result);
                if(result.equals("Polys downloaded OK")) {
                    cTracker = new CountyTracker(cManager);
                    cTracker.addCountyChangeListener(sManager);
                    initialised = true;
                }
            }
        };
        countyLoaderTask.setDialogDetails("Loading...", "Loading county polygons...");
        countyLoaderTask.execute();
    }

    // Given a new location, updates the signposts
    // Does the following:
    // - tries to find a junction at point p
    // - if one is found, call the junction handler of the signpost manager

    public void signpostUpdate(Point p) {
        new AsyncTask<Point, String, Point>() {
            public Point doInBackground(Point... pt) {
                Point junction = null;

                try {
                    loadingCounty = true;


                    if (jManager.hasDataset()) {
                        junction = jManager.getJunction(pt[0]);
                        if (junction != null && sManager.hasDataset()) {
                            sManager.onJunction(junction);
                        }
                        else {
                            publishProgress("Can't call onJunction()", "Junction: " +
                                    junction + " sManager has dataset?" + sManager.hasDataset());
                        }
                    } else {
                        publishProgress("Can't call onJunction()", "jManager.hasDataset() returned false");
                    }
                }
                catch(Exception e) {publishProgress("Exception: ", "signpostUpdate() AsyncTask" +
                        e.toString()); }
                return junction;
            }

            public void onProgressUpdate(String... values) {
                activity.addLog(values[0], values[1], true);
            }

            public void onPostExecute(Point junction) {
                activity.showIndirectText();
                if (junction != null) {
                    ArrayList<Way> jWays = jManager.getStoredWays();
                    String details = "";
                    for (int i = 0; i < jWays.size(); i++) {
                        details +=
                                (i == 0 ? "" : ",") + (jWays.get(i).getValue("name") == null ?
                                        jWays.get(i).getId() : jWays.get(i).getValue("name"))
                                        + "(" + jWays.get(i).getValue("highway") + ")";
                    }

                    activity.addLog("Finished",junction.toString() + ":" + details +
                            " onJunction() call time: "+ sManager.callTime);
                }

                loadingCounty = false;
            }
        }.execute(p);

        // CountyTracker will be null if error loading the counties or not loaded yet
        // This shouldn't go in an AsyncTask as it creates one itself to load the graph
        if (cTracker != null) {
            cTracker.update(p);
        }
    }

    public boolean isInitialised() {
        return initialised;
    }

    public void setSignpostDataset(DownloadDataTask.ReceivedData data) {
        jManager.setDataset(new OSMTiles(data.osm));
        sManager.setDataset(new OSMTiles(data.osm));
    }
}
