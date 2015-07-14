package freemap.hikar;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;
import android.widget.EditText;
import android.content.Intent;

public class LocationEntryActivity extends Activity
{
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.locationentry);
        Button okButton = (Button)findViewById(R.id.btnOkLocationEntry),
                cancelButton = (Button)findViewById(R.id.btnCancelLocationEntry);
        okButton.setOnClickListener(new View.OnClickListener()
            {
                public void onClick (View view)
                {
                    EditText editLon = (EditText)findViewById(R.id.editLonLocationEntry),
                               editLat = (EditText)findViewById(R.id.editLatLocationEntry);
                    double lon = Double.parseDouble(editLon.getText().toString()),
                            lat = Double.parseDouble(editLat.getText().toString());
                    Intent intent = new Intent();
                    intent.putExtra("freemap.hikar.lon", lon);
                    intent.putExtra("freemap.hikar.lat", lat);
                    setResult (RESULT_OK, intent);
                    finish();
                }
            });   
        cancelButton.setOnClickListener(new View.OnClickListener()
            {
                public void onClick(View view)
                {
                    finish();
                }
            });
    }
}
