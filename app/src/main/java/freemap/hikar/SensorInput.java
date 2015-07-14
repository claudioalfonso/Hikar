
package freemap.hikar;

import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.view.Surface;
import android.view.WindowManager;

import android.content.Context;

public class SensorInput implements SensorEventListener
{
    Context ctx;
    SensorInputReceiver receiver;
    Sensor accelerometer, magneticField;
    float[] accelValues, magValues;
    float k; // smoothing factor, low-pass filter
    
    public interface SensorInputReceiver
    {
        public void receiveSensorInput(float[] rotMtx);
    }
    
    public SensorInput(SensorInputReceiver receiver)
    {
        this.receiver = receiver;
        accelValues = new float[3];
        magValues = new float[3];
        k=0.1f;
    }
    
    public boolean start()
    {
        SensorManager sMgr = (SensorManager)ctx.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticField = sMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        
        if(accelerometer!=null && magneticField!=null)
        {
            sMgr.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_UI);
            sMgr.registerListener(this,magneticField, SensorManager.SENSOR_DELAY_UI);
            return true;
        }
        return false;
    }
    
    public void stop()
    {
        SensorManager sMgr = (SensorManager)ctx.getSystemService(Context.SENSOR_SERVICE);
        sMgr.unregisterListener(this,accelerometer);
        sMgr.unregisterListener(this,magneticField);
    }
    
    public void detach()
    {
        ctx = null;
    }
    
    public void attach(Context ctx)
    {
        this.ctx=ctx;
    }
    
    public void onAccuracyChanged(Sensor sensor, int acc)
    {
    
    }
    
    // Two examples (devx.com article and Photos Around)
    // recommend using an exponential smoothing method
    // on the data with a factor of 0.05 or 0.075.
    
    public void onSensorChanged(SensorEvent ev)
    {
        float[] R = new float[16], I = new float[16], glR = new float[16];
        float[] orientation = new float[3];
        switch(ev.sensor.getType())
        {
            case Sensor.TYPE_ACCELEROMETER:
                for(int i=0; i<3; i++)
                    accelValues[i] = (1-k)*accelValues[i] + k*ev.values[i];
                break;
                
            case Sensor.TYPE_MAGNETIC_FIELD:
                for(int i=0; i<3; i++)
                    magValues[i] = (1-k)*magValues[i] + k*ev.values[i];
                break;
        }
        
        if(accelValues!=null && magValues!=null && ctx!=null)
        {
            SensorManager.getRotationMatrix(R, I, accelValues, magValues);
            
            
            WindowManager wm = (WindowManager)ctx.getSystemService(Context.WINDOW_SERVICE);
            switch(wm.getDefaultDisplay().getRotation())
            {
                case Surface.ROTATION_90:
                    SensorManager.remapCoordinateSystem(R,SensorManager.AXIS_Y,SensorManager.AXIS_MINUS_X,glR);
                    break;
             
                case Surface.ROTATION_180:
                    SensorManager.remapCoordinateSystem(R,SensorManager.AXIS_MINUS_X,SensorManager.AXIS_MINUS_Y,glR);
                    break;   
                
                case Surface.ROTATION_270:
                    SensorManager.remapCoordinateSystem(R,SensorManager.AXIS_MINUS_Y,SensorManager.AXIS_X,glR);
                    break;
                
                case Surface.ROTATION_0:
                    System.arraycopy(R, 0, glR, 0, 16);
                    break;
            
            }
            
            
            receiver.receiveSensorInput(glR);
        }
    }
}