package freemap.hikar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

/**
 * Created by nick on 22/02/18.
 */

public class ModeSelector extends AppCompatActivity implements View.OnClickListener,
    Spinner.OnItemSelectedListener{

    int mode = 0;
    String[] displayProjectionSystems = { "27700", "3785" };

    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_mode_selector);
        Button btnOkModeEntry = (Button)findViewById(R.id.btnOkModeEntry);
        btnOkModeEntry.setOnClickListener(this);
        Spinner spinner= (Spinner)findViewById(R.id.spMode);
        spinner.setOnItemSelectedListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mode = prefs.getInt("prefMode", 0);
        spinner.setSelection(mode, true);
        updateDisplayProjection();
    }

    public void onClick(View view) {
        sendResultBack();
    }

    public void onItemSelected (AdapterView<?> parent, View view, int position, long id) {
        mode = position;
        updateDisplayProjection();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        mode = 0;
    }

    private void updateDisplayProjection() {
        EditText etDisplayProjection = (EditText)findViewById(R.id.etDisplayProjection);
        etDisplayProjection.setText(displayProjectionSystems[mode]);
    }

    private void sendResultBack() {
        EditText etDisplayProjection = (EditText)findViewById(R.id.etDisplayProjection);
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putInt("freemap.hikar.mode", mode);
        bundle.putString("freemap.hikar.displayProjection", etDisplayProjection.getText().toString());
        intent.putExtras(bundle);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("prefMode", mode);
        editor.commit();
        setResult(RESULT_OK, intent);
        finish();
    }
}
