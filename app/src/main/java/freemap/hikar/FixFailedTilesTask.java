package freemap.hikar;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.util.HashMap;

import freemap.andromaps.DialogUtils;
import freemap.data.Point;
import freemap.datasource.Tile;

public class FixFailedTilesTask extends DownloadDataTask {

    public FixFailedTilesTask(Context ctx, Receiver receiver, HUD hud, OsmDemIntegrator integrator) {
        super(ctx, receiver, hud, integrator, true, "Reloading failed tiles..");
    }

    public boolean updateData(Point p) throws Exception {
        return integrator.fetchFailedTiles();
    }
}
