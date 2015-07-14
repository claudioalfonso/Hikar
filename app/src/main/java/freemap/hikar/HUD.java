package freemap.hikar;

import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.content.Context;

public class HUD extends View{

    float[] orientation;
    float height, hfov, orientationAdjustment;
    Paint paint;
    
    public HUD(Context ctx)
    {
        super(ctx);
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(24);
        orientation = new float[3];
        hfov = -1.0f;
    }
    
    public void setOrientation(float[] orientation)
    {
        if(orientation.length>=3)
        {
            for(int i=0; i<3; i++)
                this.orientation[i] = (float)(orientation[i]*180.0/Math.PI);
        }
    }
    
    public void setHeight(float height)
    {
        this.height = height;    
    }
    
    public void setHFOV(float hfov)
    {
        this.hfov = hfov;
    }
   
    public void changeOrientationAdjustment(float diff)
    {
        this.orientationAdjustment += diff;
    }
    
    public void onDraw (Canvas canvas)
    {
        super.onDraw(canvas);
       
       
        if(orientation!=null)
        {
           
            String data = String.format ("Azimuth %8.3f (adj %5.1f) pitch %8.3f roll %8.3f ht %8.3f hfov %8.3f", 
                                            orientation[0], orientationAdjustment, orientation[1],
                                            orientation[2], height, hfov);
            canvas.drawText(data, 0, getHeight()-24, paint);
            
        }
    }
}
