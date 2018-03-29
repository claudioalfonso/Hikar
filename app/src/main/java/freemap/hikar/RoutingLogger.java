package freemap.hikar;

/**
 * Created by nick on 27/03/18.
 */

public interface RoutingLogger {
    public void addLog (String title, String details);
    public void addLog (String title, String details, boolean immediateUpdate);
}
