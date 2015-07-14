package freemap.hikar;

import android.location.LocationManager;
import android.location.LocationListener;
import android.location.Location;
import android.location.LocationProvider;
import android.content.Context;
import android.os.Bundle;

public class LocationProcessor implements LocationListener {

    public interface Receiver
    {
        public void receiveLocation(Location loc);
        public void noGPS();
    }
    
    Receiver receiver;
    LocationManager mgr;
    long time;
    float distance;
    
    
    public LocationProcessor(Context ctx,Receiver receiver, long time, float distance)
    {
        this.receiver=receiver;
        mgr = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
        this.time=time;
        this.distance=distance;
    }
    
    public void startUpdates()
    {
        mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, time, distance, this);
    }
    
    public void stopUpdates()
    {
        mgr.removeUpdates(this);
    }
   
    public void onLocationChanged(Location loc)
    {
        receiver.receiveLocation(loc);
    }
    
    public void onProviderEnabled(String provider)
    {
        
    }
    
    public void onProviderDisabled(String provider)
    {
        receiver.noGPS();
    }
    
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        switch(status)
        {
            case LocationProvider.OUT_OF_SERVICE:
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                receiver.noGPS();
                break;
        }
    }
}
