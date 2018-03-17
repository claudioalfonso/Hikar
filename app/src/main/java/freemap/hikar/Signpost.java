package freemap.hikar;
/**
 * Created by nick on 09/06/15.
 */

import java.util.ArrayList;
import freemap.data.Point;
import freemap.data.Algorithms;
import java.text.DecimalFormat;

public class Signpost
{
    ArrayList<Arm> arms;
    Point location;

    public Signpost (Point point)
    {
        this.location = point;
        this.arms = new ArrayList<Arm>();

    }

    public void addArm (Arm arm)
    {
        arms.add(arm);
    }

    public double distanceTo(Point loc)
    {
        return Algorithms.haversineDist (location.x, location.y, loc.x, loc.y);
    }

    public Arm getArmWithBearing (double bearing)
    {
        for(Arm a: arms)
        {
            if(Math.abs(a.getBearing()-bearing) < 1.0)
                return a;
        }
        return null;
    }

    public String toString()
    {
        DecimalFormat df=new DecimalFormat("#.##");
        String s = "Lat: " + df.format(location.y) + " lon:" + df.format(location.x) +"\n";
        for (int i=0; i<arms.size(); i++)
            s+="ARM " + i + ":\n" + arms.get(i).toString();
        return s;
    }
}
