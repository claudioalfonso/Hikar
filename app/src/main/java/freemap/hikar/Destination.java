package freemap.hikar;

/**
 * Created by nick on 09/06/15.
 */

import freemap.data.POI;
import java.text.DecimalFormat;

// Represents a destination on a signpost.
public class Destination {

    POI poi;
    double distance;

    public Destination (POI poi, double distance)
    {
        this.poi = poi;
        this.distance = distance;
    }

    public String toString()
    {
        DecimalFormat df=new DecimalFormat("#.##");
        return poi.getValue("name") + " " + df.format(distance) + " km";
    }

    public String getName()
    {
        return poi.getValue("name");
    }

    public String getType()
    {
        return poi.getValue("class");
    }
}

