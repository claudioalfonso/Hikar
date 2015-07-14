package freemap.hikar;

import android.app.Activity;
import android.preference.PreferenceFragment;
import android.os.Bundle;

public class Preferences extends Activity {
    
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefFragment()).commit();
       
    }
    
    public static class PrefFragment extends PreferenceFragment
    {
        public PrefFragment()
        {
        
        }
        
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }
    }

}
