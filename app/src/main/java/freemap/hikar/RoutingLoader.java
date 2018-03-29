package freemap.hikar;

/**
 * Created by nick on 07/05/15.
 */




    // Routing
    // Download a county routing file OR use the existing one
    // setup a GraphHopper using the data
    // find a route

// 51.0070, -0.9410 to 50.9177, -1.3753

import android.os.AsyncTask;
import android.os.Environment;
import android.content.Context;
import java.io.File;
import com.graphhopper.GraphHopper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.util.Parameters;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PointList;

import freemap.andromaps.HTTPCommunicationTask;
import freemap.andromaps.ConfigChangeSafeTask;
import freemap.routing.RegionInfo;

public class RoutingLoader implements HTTPCommunicationTask.Callback {

    GraphHopper gh;
    Context ctx;
    RoutingLoader.Callback rmCallback;


    public interface Callback
    {
        public void graphLoaded(GraphHopper gh);
        public void showText(String text);
    }

    public RoutingLoader(Context ctx, RoutingLoader.Callback rmCallback)
    {
        this.ctx=ctx;
        this.rmCallback = rmCallback;
    }

    public void downloadOrLoad(RegionInfo regionInfo) {
        if (osmFileExists(regionInfo)) {
            loadGH(regionInfo);
        }
        else {
            OSMDownloader downloader = new OSMDownloader(ctx, this, regionInfo);
            downloader.execute();
        }
    }

    private boolean osmFileExists (RegionInfo regionInfo) {
        return new File(OSMDownloader.getLocalOSMFile(regionInfo)).exists();
    }

    private void loadGH(RegionInfo regionInfo) {
        ConfigChangeSafeTask<RegionInfo, String> task = new ConfigChangeSafeTask<RegionInfo, String>(ctx) {

                GraphHopper gh;
                String error;

                public String doInBackground(RegionInfo... regionInfo) {

                    try {
                        publishProgress("Creating GraphHopper object...");
                        gh = new GraphHopperOSM().forMobile();
                        gh.setDataReaderFile(OSMDownloader.getLocalOSMFile(regionInfo[0]));
                        gh.setGraphHopperLocation(Environment.getExternalStorageDirectory().getAbsolutePath()+"/gh/" +
                            regionInfo[0].getDirectoryStructure());
                        gh.setEncodingManager(new EncodingManager("foot"));
                        publishProgress("GraphHopper: importing the OSM file...");
                        gh.importOrLoad();

                    } catch (Exception e) {
                        error = e.toString();
                        return error;
                    }
                    return "OK";
                }

                public void onProgressUpdate(String... msg) {
                    rmCallback.showText(msg[0]);
                }

                public void onPostExecute(String status) {
                    super.onPostExecute(status);
                    if (status.equals("OK")) {
                        RoutingLoader.this.gh = gh;
                        rmCallback.graphLoaded(gh);
                    }
                }
            };
        task.setDialogDetails("Loading", "Loading routing for " + OSMDownloader.getLocalOSMFile(regionInfo));
    }


    public void calcPath (double fromLat, double fromLon, double toLat, double toLon)
    {
        if(gh!=null)
        {
            new AsyncTask<Double,Void,GHResponse>() {

                public GHResponse doInBackground (Double... coords)
                {

                    GHRequest req = new GHRequest(coords[0], coords[1], coords[2], coords[3]).setAlgorithm
                            (Parameters.Algorithms.DIJKSTRA_BI);
                    req.setVehicle("foot");
                    //req.getHints().put("instructions", "false");
                    GHResponse resp = gh.route(req);
                    return resp;
                }

                public void onPostExecute (GHResponse resp) {

                    PathWrapper pw = resp.getBest();
                    String output = "Distance: " + pw.getDistance() + "\n";
                    PointList list = pw.getPoints();
                    for (int i = 0; i < list.getSize(); i++) {
                        output += "Lat: " + list.getLatitude(i) + " lon: " + list.getLongitude(i) +
                                "\n";
                    }
                    output += pw.getInstructions().toString();

                    rmCallback.showText(output);
                }
            }.execute(fromLat, fromLon, toLat, toLon);
        }
        else
        {
            rmCallback.showText("Graph is not loaded");
        }
    }

    /* needs to go in the app
    public void showText(String text)
    {
        TextView tvResults = (TextView)findViewById(R.id.tvResults);
        tvResults.setText(text);
    }

    public void onDestroy()
    {
        super.onDestroy();
        if(gh!=null)
            gh.close();
    }
    */

    public void downloadFinished(int id, Object addData) {
        loadGH((RegionInfo)addData); // addData = county
    }

    public void downloadCancelled(int id) {

    }

    public void downloadError(int id) {

    }
}