package freemap.hikar;

import freemap.andromaps.DataCallbackTask;
import android.content.Context;
import freemap.data.Point;
import java.util.HashMap;
import freemap.datasource.Tile;

import freemap.datasource.FreemapDataset;

public class DownloadDataTask extends DataCallbackTask<Point,Void> {

    OsmDemIntegrator integrator;
    boolean sourceGPS;
    
    public static class ReceivedData
    {
        public HashMap<String, Tile> osm, dem;
        
        public ReceivedData(HashMap<String,Tile> o, HashMap<String, Tile> d)
        {
            osm = o;
            dem = d;
        }
    }
    
    public interface Receiver
    {
        public void receiveData(ReceivedData data, boolean sourceGPS);
    }
    
    Receiver receiver;
    
    public DownloadDataTask(Context ctx, Receiver receiver, OsmDemIntegrator integrator, boolean sourceGPS)
    {
        super(ctx);
        this.receiver=receiver;
        this.integrator=integrator;
        this.sourceGPS = sourceGPS;
    }
    
    public String doInBackground(Point... p)
    {
        boolean status=false;
        String msg="";
        try
        {
           // msg += " p=" + p[0].x + "," + p[0].y + " ";
           status = integrator.update(p[0]);
            if(status)
            {
                //msg += " orig nDems=" + integrator.getCurrentDEMTiles().size()+ " " + " nOsms=" + integrator.getCurrentOSMTiles().size() + ". ";
                ReceivedData rd = new ReceivedData(integrator.getCurrentOSMTiles(),
                            integrator.getCurrentDEMTiles());
                
                int i=0;
               // msg += " rd.nDems=" + rd.dem.size()+ " " + " nOsms=" + rd.osm.size() + ". ";
                for(HashMap.Entry<String,Tile> e: rd.osm.entrySet())
                {
                    FreemapDataset ds = (FreemapDataset)e.getValue().data;
                   // msg += ((i++)+" " +ds.nWays() + "w, ");
                }
                msg = "Downloaded OK";
                setData(rd);
            }
            else
                   msg += " OSMDemIntegrator.update() returned false";
        }
        catch(java.io.IOException e)
        {
            msg= e.toString();     
        }
        catch(org.xml.sax.SAXException e)
        {
            msg= e.toString();
        }
        catch(org.json.JSONException e)
        {
            android.util.Log.e("hikar", "JSON parsing error: " + e.getStackTrace());
            msg="JSONException:" + e.toString();
        }
        catch(Exception e)
        {
            android.util.Log.e("hikar", "Internal error: " + e.getStackTrace());
            msg="Internal error: " + e.toString();
        }
        //return (status) ? msg /*"Successfully downloaded"*/ : "Error downloading: " + msg;
        return msg;
    }
   
    
    public void receive(Object data)
    {
        if(receiver!=null)
            receiver.receiveData((ReceivedData)data, sourceGPS);
    }
}
