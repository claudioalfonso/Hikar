package freemap.hikar;

/**
 * Created by nick on 09/06/15.
 */

import java.util.ArrayList;

public class Arm {

    ArrayList<Destination> destinations;
    double bearing;

    public Arm (double bearing)
    {
        this.bearing = bearing;
        this.destinations = new ArrayList<Destination>();
    }

    public void addDestination(Destination d)
    {
        destinations.add(d);
    }

    public double getBearing ()
    {
        return bearing;
    }

    public String toString()
    {
        String s = "";
        for(int i=0; i<destinations.size(); i++)
            s += destinations.get(i).toString()+"\n";
        s+="Bearing: " + bearing;
        return s;
    }
}
