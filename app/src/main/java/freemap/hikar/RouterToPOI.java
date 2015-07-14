package freemap.hikar;

import android.os.AsyncTask;

import com.graphhopper.GraphHopper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.AlgorithmOptions;
import freemap.data.Point;
import freemap.data.POI;

/**
 * Created by nick on 27/05/15.
 */

// Finds a route from a given point to a given POI using a given GraphHopper

public class RouterToPOI {

    GraphHopper gh;
    Callback callback;


    public interface Callback
    {
        public void pathCalculated(GHResponse response, POI poi);
    }

    public RouterToPOI(GraphHopper gh, Callback callback)
    {
        this.gh = gh;
        this.callback = callback;
    }

    public boolean  calcPath (Point curLoc, POI poi) {

        if (gh != null) {
            class CalcPathTask extends AsyncTask<Double, Void, GHResponse> {

                POI poi;

                public CalcPathTask (POI poi)
                {
                    this.poi = poi;
                }

                public GHResponse doInBackground(Double... coords) {

                    GHRequest req = new GHRequest(coords[0], coords[1], coords[2], coords[3]).setAlgorithm
                            (AlgorithmOptions.DIJKSTRA_BI);
                    req.setVehicle("foot");
                    //req.getHints().put("instructions", "false");
                    GHResponse resp = gh.route(req);
                    return resp;
                }

                public void onPostExecute(GHResponse resp) {

                    callback.pathCalculated(resp, poi);
                    /*
                    String output = "Distance: " + resp.getDistance() + "\n";
                    PointList list = resp.getPoints();
                    for (int i = 0; i < list.getSize(); i++) {
                        output += "Lat: "  + list.getLatitude(i) + " lon: " + list.getLongitude(i) +
                                "\n";
                    }
                    output += resp.getInstructions().toString();

                    showText(output);
                    */
                }
            }

            new CalcPathTask(poi).execute(curLoc.y, curLoc.x, poi.getPoint().y, poi.getPoint().x);
            return true;
        } else {
            return false;
        }
    }
}
