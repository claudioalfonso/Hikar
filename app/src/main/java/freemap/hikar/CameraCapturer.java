package freemap.hikar;

import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import java.io.IOException;

public class CameraCapturer implements SurfaceTexture.OnFrameAvailableListener {

    Camera camera;
    OpenGLView.DataRenderer glRenderer;
   
    public CameraCapturer(OpenGLView.DataRenderer renderer)
    {
        glRenderer = renderer;
    }
    
    public void openCamera()
    {
        try
        {
            camera = Camera.open();
        }
        catch (Exception e) { } 
    }
    
    public void startPreview(SurfaceTexture surfaceTexture) throws IOException
    {
        camera.setPreviewTexture(surfaceTexture);
        surfaceTexture.setOnFrameAvailableListener(this);
        camera.startPreview();
        Camera.Parameters params = camera.getParameters();
        android.util.Log.d("hikar","hfov=" + params.getHorizontalViewAngle() +
                                " vfov=" + params.getVerticalViewAngle());
    }
    
    public void stopPreview()
    {
        camera.stopPreview();
        releaseCamera();
    }
    
    public void releaseCamera()
    {
        if(camera!=null)
        {
            camera.release();
            camera=null;
        }
    }  
    
    public void onFrameAvailable(SurfaceTexture st)
    {
        // This appears not to be necessary; SurfaceTexture.updateTexImage() on its own will suffice
        // the converse is not true: this without SurfaceTexture.updateTexImage() will not work
         glRenderer.setCameraFrame(st);
    }
    
    public float getHFOV()
    {
        if(camera!=null)
        {
            Camera.Parameters params = camera.getParameters();
            if(params!=null)
                return params.getHorizontalViewAngle();
        }
        return -1.0f;
    }
    
    public boolean isActive()
    {
        return camera!=null;
    }
}
