package freemap.hikar;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import freemap.data.Way;
import freemap.data.Projection;
import android.util.Log;

public class JSONWayFactory {

    Projection proj;
    
    public JSONWayFactory(Projection proj)
    {
        this.proj = proj;
    }
    
    public Way jsonToWay(String json) throws JSONException
    {
        JSONObject jsonObj = new JSONObject(json);
        String geomType = jsonObj.getString("type");
        Way way = new Way();
        
        if(geomType.equals("LineString"))
        {
           JSONArray arr = jsonObj.getJSONArray("coordinates");
           wayFromLinestring(way, arr);
            
        }
        else if (geomType.equals("MultiLineString"))
        {
            JSONArray arr = jsonObj.getJSONArray("coordinates");
            for(int i=0; i<arr.length(); i++)
            {
                wayFromLinestring(way, arr.getJSONArray(i));
            }
        }
        
        way.reproject(proj);
        return way;
    } 
    
    private static void wayFromLinestring(Way way, JSONArray arr) throws JSONException
    {
        for(int i=0; i<arr.length(); i++)
        {
            JSONArray curCoord = arr.getJSONArray(i);
            double x = curCoord.getDouble(0), y = curCoord.getDouble(1);
            way.addPoint(x, y);
        }
    }
    
    public static void test () throws JSONException
    {
        JSONWayFactory fact = new JSONWayFactory(null);
        Way w = fact.jsonToWay("{'type': 'MultiLineString', 'coordinates': [[[-1,51],[-1.1,51.1],[-1.2,51.2]]]}");
        Log.d("hikar", "Way from JSON="+w.toString());
    }
}
