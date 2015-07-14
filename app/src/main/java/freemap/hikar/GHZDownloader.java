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
public class GHZDownloader extends DownloadBinaryFilesTask
{

    String county;

    public GHZDownloader (Context ctx, HTTPCommunicationTask.Callback callback, String county) {
        super(ctx, new String[]{"http://www.free-map.org.uk/downloads/gh/" + county + "-latest.ghz"},
                new String[]{Environment.getExternalStorageDirectory().getAbsolutePath() +
                        "/gh/" + county + ".osm-gh"}, "Download files", callback, 0);
        this.setDialogDetails("Downloading", "Downloading routing data...");
        this.setAdditionalData(county);
        this.county = county;
    }

    // http://stackoverflow.com/questions/23869228/how-to-read-file-from-zip-using-inputstream
    public void doWriteFile(InputStream in, String outputFile) throws IOException, ZipException
    {
        //    super.doWriteFile(in,outputFile);
        Log.d("hikar", "output file: " + outputFile);
        String dir = outputFile;
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(in);
            ZipEntry curEntry = null;
            FileOutputStream out = null;
            byte[] buf = new byte[2048];
            while ((curEntry = zis.getNextEntry()) != null) {
                try {

                    Log.d("hikar", "Name=" + curEntry.getName());
                    File fDir = new File(dir);
                    if(!fDir.exists())
                        fDir.mkdir();
                    out = new FileOutputStream(dir + "/" + curEntry.getName());
                    int len = 0;
                    while ((len = zis.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }

                }

                finally {
                    if(out!=null) out.close();
                }
            }
        }

        finally {
            if(zis!=null) zis.close();
        }
    }
}
