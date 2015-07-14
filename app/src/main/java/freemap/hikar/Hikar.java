package freemap.hikar;

import freemap.andromaps.DialogUtils;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.view.Menu;
import android.view.ViewGroup.LayoutParams;
import android.view.MenuItem;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.location.LocationManager;


public class Hikar extends Activity implements ViewFragment.HUDProvider
{
    LocationProcessor locationProcessor;
    ViewFragment viewFragment;
    HUD hud;
    boolean autoStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hud=new HUD(this);
        setContentView(R.layout.activity_main);
        addContentView(hud, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        viewFragment = (ViewFragment)getFragmentManager().findFragmentById(R.id.view_fragment);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if(prefs != null)
        {
            float orientationAdjustment = prefs.getFloat("orientationAdjustment", 0.0f);
            viewFragment.changeOrientationAdjustment(orientationAdjustment);
            hud.changeOrientationAdjustment(orientationAdjustment);

            autoStart = prefs.getBoolean("autoStart", true);

        }

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu)
    {
        MenuItem startMenuItem = menu.findItem(R.id.menu_start);
        startMenuItem.setTitle(autoStart ? "Stop":"Start");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        boolean retcode=false;

        switch(item.getItemId())
        {
            case R.id.menu_calibrate:
                viewFragment.toggleCalibrate();
                item.setTitle(item.getTitle().equals("Calibrate") ? "Stop calibrating": "Calibrate");
                retcode=true;
                break;

            case R.id.menu_settings:
                Intent i = new Intent(this,Preferences.class);
                startActivity(i);
                break;

            case R.id.menu_start:
                boolean start = item.getTitle().equals("Start");
                viewFragment.setActivate(start);
                item.setTitle(start ? "Stop" : "Start");
                break;

            case R.id.menu_location:
                LocationManager mgr = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

                if(!mgr.isProviderEnabled(LocationManager.GPS_PROVIDER))
                {
                    Intent intent = new Intent(this, LocationEntryActivity.class);
                    startActivityForResult (intent, 0);
                }
                else
                {
                    DialogUtils.showDialog(this, "Can only manually specify location when GPS is off");
                }
                break;

        }
        return retcode;
    }

    public void onPause()
    {
        super.onPause();
    }

    public void onStart()
    {
        super.onStart();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        float cameraHeight = Float.parseFloat(prefs.getString("prefCameraHeight","1.4"));
        viewFragment.setCameraHeight(cameraHeight);
        String prefSrtmUrl=prefs.getString("prefSrtmUrl","http://www.free-map.org.uk/ws/"),
                prefLfpUrl=prefs.getString("prefLfpUrl", "http://www.free-map.org.uk/downloads/lfp/"),
                prefOsmUrl=prefs.getString("prefOsmUrl", "http://www.free-map.org.uk/fm/ws/");
        boolean urlchange = viewFragment.setDataUrls(prefLfpUrl, prefSrtmUrl, prefOsmUrl);
        int prefDEM = Integer.valueOf(prefs.getString("prefDEM","0"));
        viewFragment.setDEM(prefDEM);
        String prefDisplayProjectionID = "epsg:" + prefs.getString("prefDisplayProjection", "27700");
        if(!viewFragment.setDisplayProjectionID(prefDisplayProjectionID))
            DialogUtils.showDialog(this, "Invalid projection " + prefDisplayProjectionID);
        else if (autoStart)
            viewFragment.setActivate(true);
    }

    public void onActivityResult (int requestCode, int resultCode, Intent intent)
    {
        if(resultCode == Activity.RESULT_OK)
        {
            switch (requestCode)
            {
                case 0:
                    Bundle info = intent.getExtras();
                    double lon = info.getDouble("freemap.hikar.lon"), lat = info.getDouble("freemap.hikar.lat");
                    android.util.Log.d("hikar", "setting locaton to " + lon + "," + lat);
                    viewFragment.setLocation(lon, lat);
                    break;
            }
        }
    }

    public boolean onKeyDown(int key, KeyEvent ev)
    {

        boolean handled=false;

        switch(key)
        {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                viewFragment.changeOrientationAdjustment(-1.0f);
                hud.changeOrientationAdjustment(-1.0f);
                handled=true;
                break;

            case KeyEvent.KEYCODE_VOLUME_UP:
                viewFragment.changeOrientationAdjustment(1.0f);
                hud.changeOrientationAdjustment(1.0f);
                handled=true;
                break;
        }

        return handled ? true: super.onKeyDown(key, ev);
    }

    public boolean onKeyUp(int key, KeyEvent ev)
    {
        return key==KeyEvent.KEYCODE_VOLUME_DOWN || key==KeyEvent.KEYCODE_VOLUME_UP ? true: super.onKeyUp(key,ev);
    }

    public void onDestroy()
    {
        super.onDestroy();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("orientationAdjustment", viewFragment.getOrientationAdjustment());
        editor.commit();
    }

    public HUD getHUD()
    {
        return hud;
    }
}
