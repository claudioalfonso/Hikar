package freemap.hikar;

import freemap.andromaps.DownloadBinaryFilesTask;
import freemap.andromaps.HTTPCommunicationTask;
import android.content.Context;
import android.os.Environment;
import java.io.IOException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.File;
import android.util.Log;

/**
 * Created by nick on 05/05/15.
 */


public class OSMDownloader extends DownloadBinaryFilesTask
{




    static final String outputDir = Environment.getExternalStorageDirectory().getAbsolutePath() +
            "/gh/osmfiles/";

    public OSMDownloader(Context ctx, HTTPCommunicationTask.Callback callback, RegionInfo regionInfo) {

        super(ctx, new String[]{ regionInfo.getGeofabrik() }, new String[] { outputDir+regionInfo.getLocalOSMFile() },
                "Download files", callback, 0);
        File dir = new File(outputDir);
        if(!dir.exists()) {
            dir.mkdirs();
        }
        this.setDialogDetails("Downloading", "Downloading OSM data for routing...");
        this.setAdditionalData(regionInfo);
    }
}
