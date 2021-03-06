package freemap.hikar;


import android.os.AsyncTask;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;

import freemap.data.POI;
import freemap.data.Point;

/**
 * Created by nick on 27/05/15.
 */

// Finds a route from a given point to a given POI using a given GraphHopper


public class RouterToPOI {


    GraphHopper gh;
    Callback callback;


    public interface Callback
    {
        public void pathCalculated(PathWrapper pw, POI poi, String msg);
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
                String exceptionMsg;

                public CalcPathTask (POI poi)
                {
                    this.poi = poi;
                }

                public GHResponse doInBackground(Double... coords) {

                    try {
                        GHRequest req = new GHRequest(coords[0], coords[1], coords[2], coords[3]).setAlgorithm
                                (Parameters.Algorithms.DIJKSTRA_BI);
                        req.setVehicle("foot");
                        //req.getHints().put("instructions", "false");
                        GHResponse resp = gh.route(req);
                        return resp;
                    } catch(Exception e) {
                        exceptionMsg = e.toString();
                        return null;
                    }
                }

                public void onPostExecute(GHResponse resp) {

                    String output;
                    if(resp!=null) {
                        PathWrapper pw = null;
                        try {
                            pw = resp.getBest();


                            output = "Distance: " + pw.getDistance() + "\n";
                            PointList list = pw.getPoints();
                            for (int i = 0; i < list.getSize(); i++) {
                                output += "Lat: " + list.getLatitude(i) + " lon: " + list.getLongitude(i) +
                                        "\n";
                            }
                            output += pw.getInstructions().toString();

                        } catch(Exception e) {
                            output = e.toString();
                        }
                        callback.pathCalculated(pw, poi, output);
                    } else {
                        output = exceptionMsg;
                        callback.pathCalculated(null, poi, output);
                    }
                }
            }

            new CalcPathTask(poi).execute(curLoc.y, curLoc.x, poi.getPoint().y, poi.getPoint().x);
            return true;
        } else {
            return false;
        }
    }

}
