package freemap.hikar;

/**
 * Created by nick on 27/05/15.
 */


    // SignpostManager
    // called when we get to a junction
    // 1. find all nearby POIs of certain types
    // 2. route to each POI and get a distance
    // 3. draw signposts based on the initial route to each POI

    // Routing
    // Download a county route OR use the existing one
    // setup a GraphHopper using the data
    // find a route

// 51.0070, -0.9410 to 50.9177, -1.3753

import freemap.datasource.FreemapDataset;
import freemap.data.POI;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import android.content.Context;
import freemap.andromaps.DialogUtils;
import freemap.data.Point;
import java.util.ArrayList;
import freemap.datasource.OSMTiles;
import freemap.routing.Signpost;
import freemap.routing.Arm;
import freemap.routing.Destination;
import freemap.routing.County;
import freemap.routing.CountyTracker;

public class SignpostManager implements RouteLoader.Callback, RouterToPOI.Callback, FreemapDataset.POIVisitor,
                                        CountyTracker.CountyChangeListener
{
    OSMTiles pois;
    GraphHopper gh;
    Context ctx;
    RouteLoader loader;
    RouterToPOI routerToPOI;
    Point curLoc;
    ArrayList<Signpost> signposts;
    Signpost curSignpost;

    public SignpostManager(Context ctx)
    {
        this.ctx = ctx;
        loader = new RouteLoader(ctx, this);
        this.pois = pois;
        this.signposts = new ArrayList<Signpost>();
    }

    public void setDataset(OSMTiles pois)
    {
        this.pois = pois;
    }

    public void onCountyChange (County county)
    {
        loader.downloadOrLoad(county.getName());
    }

    public void onJunction (Point loc)
    {
        curLoc = loc;

        for(Signpost s: signposts) {
            if (s.distanceTo(loc) < 50.0) {
                curSignpost = s;
                break;
            }
        }

        if(curSignpost == null) {
            curSignpost = new Signpost(loc);
            signposts.add(curSignpost);
            pois.operateOnNearbyPOIs(this, loc, 5000.0);
        }
    }

    public void visit (POI poi)
    {
        if(poi==null)
        {
            // Runs when we've found all the pois
        }
        else if((poi.containsKey("amenity") && poi.getValue("amenity").equals("pub")) ||
                poi.containsKey("place") ||
           (poi.containsKey("natural") && poi.getValue("natural").equals("peak")))
        {
            routerToPOI.calcPath(curLoc, poi);
        }
    }

    public void routeLoaded(GraphHopper gh)
    {
        this.gh = gh;
        routerToPOI = new RouterToPOI(gh, this);
    }

    public void showText(String msg)
    {
        DialogUtils.showDialog (ctx, msg);
    }

    // poi is the POI we're routing to
    public void pathCalculated(GHResponse response, POI poi)
    {
        // find the bearing of the first stage of the route and the total distance
        double d = response.getDistance();

        if(response.getPoints().size() >= 2) {
            double nextLat = response.getPoints().getLat(1),
                    nextLon = response.getPoints().getLon(1);

            Point p = new Point (nextLon, nextLat);

            double bearing = p.bearingFrom (curLoc);
            Arm arm;
            if ((arm=curSignpost.getArmWithBearing(bearing)) == null){
                arm = new Arm(bearing);
                curSignpost.addArm(arm);
            }
            arm.addDestination (new Destination (poi,d));
        }
    }

}