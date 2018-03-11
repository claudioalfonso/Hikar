package freemap.hikar;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;

/**
 * Created by nick on 11/03/18.
 */

public class UserGuide extends AppCompatActivity {
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView wv= new WebView(this);
        setContentView(wv);
        wv.loadUrl("file:///android_asset/userguide.html");
    }
}
