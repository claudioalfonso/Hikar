package freemap.hikar;

import freemap.andromaps.DataCallbackTask;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import freemap.andromaps.DialogUtils;
import freemap.data.Point;

import java.util.HashMap;

import freemap.datasource.Tile;

import freemap.datasource.FreemapDataset;

public class DownloadDataTask extends AsyncTask<Point, String, Boolean> {

    OsmDemIntegrator integrator;
    boolean sourceGPS;


    public static class ReceivedData {
        public HashMap<String, Tile> osm, dem;

        public ReceivedData(HashMap<String, Tile> o, HashMap<String, Tile> d) {
            osm = o;
            dem = d;
        }
    }

    public interface Receiver {
        public void receiveData(ReceivedData data, boolean sourceGPS);
    }

    Receiver receiver;
    HUD hud;
    ReceivedData data;
    String errorMsg;
    Context ctx;
    String progressMsg;

    public DownloadDataTask(Context ctx, Receiver receiver, HUD hud, OsmDemIntegrator integrator, boolean sourceGPS) {
        this(ctx, receiver, hud, integrator, sourceGPS, "Loading data...");
    }

    public DownloadDataTask(Context ctx, Receiver receiver, HUD hud, OsmDemIntegrator integrator, boolean sourceGPS,
                            String progressMsg) {
        this.ctx = ctx;
        this.receiver = receiver;
        this.integrator = integrator;
        this.sourceGPS = sourceGPS;
        this.hud = hud;
        this.progressMsg = progressMsg;
    }

    public void onPreExecute() {
        super.onPreExecute();
        Log.d("hikar", "Loading data...");
        hud.setMessage(progressMsg);
    }

    public Boolean doInBackground(Point... p) {
        boolean status = false;
        errorMsg = "";
        try {
            // msg += " p=" + p[0].x + "," + p[0].y + " ";
            status = updateData(p[0]);
            if (status) {
                //msg += " orig nDems=" + integrator.getCurrentDEMTiles().size()+ " " + " nOsms=" + integrator.getCurrentOSMTiles().size() + ". ";
                ReceivedData rd = new ReceivedData(integrator.getCurrentOSMTiles(),
                        integrator.getCurrentDEMTiles());

                int i = 0;
                Log.d("hikar", "Loaded ok. rendering data");
                publishProgress(sourceGPS ? "Loaded ok. Rendering data..." : "Loaded ok.");
                data = rd;
                if (data == null) {
                    errorMsg = "Data returned was null";
                } else {
                    status = true;
                }
            } else {
                errorMsg = "OSMDemIntegrator.update() unexpectedly returned false";
            }
        } catch (java.io.IOException e) {
            errorMsg = "Input/output error: " + e.toString();
        } catch (org.json.JSONException e) {
            android.util.Log.e("hikar", "JSON parsing error: " + e.getStackTrace());
            errorMsg = "Corrupted JSON data";
        } catch (Exception e) {
            android.util.Log.e("hikar", "Internal error: " + e.getStackTrace());
            errorMsg = "Internal error: " + e.toString();
        }

        return status;
    }


    public void onProgressUpdate(String... progressMsg) {
        hud.setMessage(progressMsg[0]);
    }


    public void onPostExecute(Boolean status) {

        if (status == false) {
            DialogUtils.showDialog(ctx, "Some or all data was not loaded. Details: " + errorMsg +
                    ". Hikar will try again shortly.");
            hud.removeMessage();
        }
        receiver.receiveData((ReceivedData) data, sourceGPS);
    }

    public boolean updateData(Point p) throws Exception {
        return integrator.update(p);
    }
}
