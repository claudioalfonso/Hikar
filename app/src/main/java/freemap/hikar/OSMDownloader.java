package freemap.hikar;

import freemap.andromaps.DownloadBinaryFilesTask;
import freemap.andromaps.HTTPCommunicationTask;
import freemap.routing.RegionInfo;

import android.content.Context;
import android.os.Environment;
import java.io.File;

/**
 * Created by nick on 05/05/15.
 */


public class OSMDownloader extends DownloadBinaryFilesTask
{




    static final String outputDir = Environment.getExternalStorageDirectory().getAbsolutePath() +
            "/gh/osmfiles/";

    public OSMDownloader(Context ctx, HTTPCommunicationTask.Callback callback, RegionInfo regionInfo) {

        super(ctx, new String[]{ regionInfo.getGeofabrik() }, new String[] { getLocalOSMFile(regionInfo) },
                "Download files", callback, 0);
        File dir = new File(outputDir);
        if(!dir.exists()) {
            dir.mkdirs();
        }
        this.setDialogDetails("Downloading", "Downloading OSM data for routing...");
        this.setAdditionalData(regionInfo);
    }

    static String getLocalOSMFile(RegionInfo regionInfo) {
        return outputDir+regionInfo.getLocalOSMFile();
    }
}
