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


import freemap.data.Algorithms;
import freemap.data.POI;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;


import android.content.Context;
import freemap.andromaps.DialogUtils;
import freemap.data.Point;

;
import java.util.ArrayList;
import freemap.datasource.OSMTiles;
import freemap.routing.County;
import freemap.routing.CountyTracker;


import java.text.DecimalFormat;


public class SignpostManager implements RoutingLoader.Callback, RouterToPOI.Callback,
                                        CountyTracker.CountyChangeListener
{
    OSMTiles pois;
    GraphHopper gh;
    Context ctx;
    RoutingLoader loader;
    RouterToPOI routerToPOI;
    Point curLoc;
    ArrayList<Signpost> signposts;
    Signpost curSignpost;
    ArrayList<Point> pendingJunctions; // pending junctions in case county being loaded

    OSMTiles.POIIterator poiIterator;
    String routingDetails;
    DecimalFormat df;
    public long callTime;
    RoutingLogger logger;

    public SignpostManager(Context ctx, RoutingLogger logger)
    {
        this.ctx = ctx;
        loader = new RoutingLoader(ctx, this);
        signposts = new ArrayList<Signpost>();
        pendingJunctions = new ArrayList<Point>();
        df=new DecimalFormat("#.##");
        this.logger = logger;
    }

    public void setDataset(OSMTiles pois)
    {
        this.pois = pois;
    }

    public void onCountyChange (County county)
    {
        gh = null; // to indicate we're loading a county
        pendingJunctions.clear(); // clear any pending junctions for old county
        // TODO for now this assumes England. Expand this once we have polygons for other areas
        loader.downloadOrLoad(new RegionInfo("europe", "great-britain", "england",county.getName()));

    }

    public void onJunction (Point loc)
    {
        // Only try and create new signpost if we're not loading a new county
        try {

            callTime = System.currentTimeMillis()/1000;
            routingDetails = "onJunction():";
            if (gh != null) {
                curLoc = loc;
                curSignpost = null;

                for (Signpost s : signposts) {
                    if (s.distanceTo(loc) < 50.0) {
                        curSignpost = s;
                        routingDetails = " FOUND SIGNPOST: " + s;
                        break;
                    }
                }

                poiIterator = pois.poiIterator();

                if (curSignpost == null) {
                    curSignpost = new Signpost(loc);
                    signposts.add(curSignpost);
                    routingDetails += "NEW SIGNPOST: " + curSignpost;




                    routingDetails += " calling nextPOI()...";
                    logger.addLog("Routing to POIs (begin)", routingDetails);
                    nextPOI();
                }
            } else // otherwise add it to pending junction list
            {
                routingDetails += " Pending: " + curLoc;
                pendingJunctions.add(curLoc);

                logger.addLog("onJunction()", routingDetails);
            }

        }
        catch(Exception e)
        {
            logger.addLog("SignpostManager/onJunction() Exception", e.toString());
        }

    }

    public void nextPOI()
    {
        boolean doCalcPath=false;
        POI p = (POI)poiIterator.next();
        routingDetails += " nextPOI():";
        while(p!=null && doCalcPath==false) {

            Point pt = p.getUnprojectedPoint();

            if((p.containsKey("amenity") && p.getValue("amenity").equals("pub")) ||
                    p.containsKey("place") ||
                    (p.containsKey("natural") && p.getValue("natural").equals("peak"))) {

                double dist = Algorithms.haversineDist(pt.x, pt.y, curLoc.x, curLoc.y);

                if(p.getValue("name")!=null)
                    routingDetails += p.getValue("name") + "=" + df.format(dist) + ",";
                if (Algorithms.haversineDist(pt.x, pt.y, curLoc.x, curLoc.y) <= 2000.0)
                {
                    doCalcPath=true;
                }


            }

            if(!doCalcPath)
                p = (POI)poiIterator.next();
                logger.addLog("Routing to POIs (part)", routingDetails);
            }

            if(doCalcPath)
                routerToPOI.calcPath(curLoc, p);
            else {
                routingDetails += " poi is null... end of POIs";
                logger.addLog("Routing to POIs", routingDetails);
            }

    }

    public void graphLoaded(GraphHopper gh)
    {
        this.gh = gh;
        logger.addLog("Loaded the graph", gh.toString());
        routerToPOI = new RouterToPOI(gh, this);



        // Process any junctions received while loading new county
        for (Point p: pendingJunctions)
            onJunction(p);
        pendingJunctions.clear();
    }

    public void showText(String msg)
    {
        DialogUtils.showDialog (ctx, msg);
    }

    // poi is the POI we're routing to
    public void pathCalculated(PathWrapper pw, POI poi)
    {


        // find the bearing of the first stage of the route and the total distance

        double d = pw.getDistance();
        routingDetails += "Dist to: " + poi.toString() + "=" + pw.getInstructions().toString();
        if(pw.getPoints().size() >= 2) {
            double nextLat = pw.getPoints().getLat(1),
                    nextLon = pw.getPoints().getLon(1);

            Point p = new Point (nextLon, nextLat);

            double bearing = p.bearingFrom (curLoc);
            Arm arm;
            if ((arm=curSignpost.getArmWithBearing(bearing)) == null){
                arm = new Arm(bearing);
                curSignpost.addArm(arm);
            }
            arm.addDestination (new Destination (poi,d));
        }
        nextPOI();

    }

    public boolean hasDataset()
    {
        return pois!=null;
    }
}

