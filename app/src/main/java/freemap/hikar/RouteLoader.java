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
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.util.PointList;
import com.graphhopper.routing.util.EncodingManager;
import freemap.andromaps.HTTPCommunicationTask;

public class RouteLoader implements HTTPCommunicationTask.Callback {

    GraphHopper gh;
    Context ctx;
    RouteLoader.Callback rmCallback;

    public interface Callback
    {
        public void routeLoaded(GraphHopper gh);
        public void showText(String text);
    }

    public RouteLoader(Context ctx, RouteLoader.Callback rmCallback)
    {
        this.ctx=ctx;
        this.rmCallback = rmCallback;
    }

        public void downloadOrLoad(String county)
        {
            if(exists(county))
                loadGH(county);
            else
            {
                GHZDownloader downloader =new GHZDownloader(ctx, this, county);
                downloader.execute();
            }
        }

        public boolean exists (String county) {
            return new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/gh/"+county+".osm-gh").isDirectory();
        }

        public void loadGH(String county) {
            AsyncTask<String, Void, Boolean> task = new AsyncTask<String, Void, Boolean>() {

                GraphHopper gh;
                String error;

                public Boolean doInBackground(String... filename) {

                    try {
                        gh = new GraphHopper().forMobile();
                        gh.setEncodingManager(new EncodingManager("foot"));
                        gh.load(filename[0]);

                    } catch (Exception e) {
                        error = e.toString();
                        return false;
                    }
                    return true;
                }

                public void onPostExecute(Boolean status) {
                    if (status) {
                        RouteLoader.this.gh = gh;
                        rmCallback.routeLoaded(gh);
                    } else
                        rmCallback.showText(error);

                }
            };
            rmCallback.showText("Loading graph");
            task.execute(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/gh/"+county+".osm-gh");
        }






    public void calcPath (double fromLat, double fromLon, double toLat, double toLon)
    {
        if(gh!=null)
        {
            new AsyncTask<Double,Void,GHResponse>() {

                public GHResponse doInBackground (Double... coords)
                {

                    GHRequest req = new GHRequest(coords[0], coords[1], coords[2], coords[3]).setAlgorithm
                            (AlgorithmOptions.DIJKSTRA_BI);
                    req.setVehicle("foot");
                    //req.getHints().put("instructions", "false");
                    GHResponse resp = gh.route(req);
                    return resp;
                }

                public void onPostExecute (GHResponse resp) {
                    String output = "Distance: " + resp.getDistance() + "\n";
                    PointList list = resp.getPoints();
                    for (int i = 0; i < list.getSize(); i++) {
                        output += "Lat: "  + list.getLatitude(i) + " lon: " + list.getLongitude(i) +
                                "\n";
                    }
                    output += resp.getInstructions().toString();

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

    public void downloadFinished(int id, Object addData)
    {
        loadGH((String)addData); // addData = county
    }

    public void downloadCancelled(int id)
    {

    }

    public void downloadError(int id)
    {

    }
}