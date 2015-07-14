package freemap.hikar;


import android.view.MotionEvent;
import android.view.View;


//see http://www.zdnet.com/blog/burnette/how-to-use-multi-touch-in-android-2-part-3-understanding-touch-events/
//1775?tag=content;siu-container

public class PinchListener implements View.OnTouchListener {
    
    float[] xDown = { -1.0f, -1.0f } , xUp = { -1.0f, -1.0f }, 
            yDown = { -1.0f,-1.0f } , yUp = { -1.0f, -1.0f };
    boolean dragging;
    
    public interface Handler
    {
        public void onPinchIn();
        public void onPinchOut();
    }
    
    Handler handler;
    
    public PinchListener(Handler h)
    {
        handler = h;
    } 

    public boolean onTouch(View view, MotionEvent ev)
    {
        
        int action= ev.getAction() & MotionEvent.ACTION_MASK, ptrIdx;
        
        switch(action)
        {
            case MotionEvent.ACTION_DOWN:
                ptrIdx = ev.getAction() >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                android.util.Log.d("hikar","ACTION_DOWN: " + ev.getPointerCount());
                android.util.Log.d("hikar",
                        "ACTION_DOWN: xDown[" + ev.getPointerId(ptrIdx) + "] =" + ev.getX() +
                        " yDown[" + ev.getPointerId(ptrIdx) + "] =" + ev.getY());
                break;
                
            case MotionEvent.ACTION_POINTER_DOWN:
                if(ev.getPointerCount()==2)
                {
                    for(int i=0; i<2; i++)
                    {
                        xDown[ev.getPointerId(i)] = ev.getX(i);
                        yDown[ev.getPointerId(i)] = ev.getY(i);
                        android.util.Log.d("hikar",
                                    "ACTION_POINTER_DOWN: xDown[" + ev.getPointerId(i) + "] =" + ev.getX(i) +
                                    " yDown[" + ev.getPointerId(i) + "] =" + ev.getY(i));
                    }
                    dragging=true;
                }
                break;
            
                case MotionEvent.ACTION_MOVE:
                    if(dragging)
                    {
                        
                        //android.util.Log.d("hikar","ACTION_MOVE");
                    }
                    break;
                    
                case MotionEvent.ACTION_POINTER_UP:
                    if(dragging)
                    {
                        ptrIdx = ev.getAction() >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                        xUp[ev.getPointerId(ptrIdx)] = ev.getX(ptrIdx);
                        yUp[ev.getPointerId(ptrIdx)] = ev.getY(ptrIdx);
                        android.util.Log.d("hikar",
                                "ACTION_POINTER_UP: xUp[" + ev.getPointerId(ptrIdx) + "] =" + ev.getX(ptrIdx) +
                                " yUp[" + ev.getPointerId(ptrIdx) + "] =" + ev.getY(ptrIdx));
                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                    if(dragging)
                    {
                        ptrIdx = ev.getAction() >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                        xUp[ev.getPointerId(ptrIdx)] = ev.getX();
                        yUp[ev.getPointerId(ptrIdx)] = ev.getY();
                        android.util.Log.d("hikar",
                                "ACTION_UP: xUp[" + ev.getPointerId(ptrIdx) + "] =" + ev.getX() +
                                " yUp[" + ev.getPointerId(ptrIdx) + "] =" + ev.getY());
                        dragging=false;
                    
                        float dxdown = xDown[1] - xDown[0],
                                dydown = yDown[1] - yDown[0],
                                ddown = (float)Math.sqrt(dxdown*dxdown + dydown*dydown),
                                dxup = xUp[1] - xUp[0],
                                dyup = yUp[1] - yUp[0],
                                dup = (float) Math.sqrt(dxup*dxup + dyup*dyup);
              
                        if(Math.abs(ddown-dup) > 50.0f)
                        {
                            if(ddown > dup) 
                                handler.onPinchIn();
                          
                            else if (ddown < dup)
                                handler.onPinchOut();
                        }
                    }  
                    for(int j=0; j<2; j++)
                        xDown[j]=xUp[j]=yDown[j]=yUp[j]=-1.0f;
                    break;
            
        }  
        return true;
    }
}
