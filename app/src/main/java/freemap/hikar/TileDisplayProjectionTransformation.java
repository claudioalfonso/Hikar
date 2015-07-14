package freemap.hikar;

import freemap.data.Projection;
import freemap.data.Point;

public class TileDisplayProjectionTransformation {

    Projection tilingProj, displayProj;
    
    
    public TileDisplayProjectionTransformation (Projection tilingProj, Projection displayProj)
    {
        this.tilingProj = tilingProj;
        this.displayProj = displayProj;
    }
    
    public Point tileToDisplay (Point p)
    {
        Point projected = new Point(p.x, p.y, p.z);
        if(!(tilingProj==null && displayProj==null || tilingProj.getID().equals(displayProj.getID())))
        { 
            
            if(tilingProj != null)
                projected = tilingProj.unproject(projected);
            if(displayProj != null)
                projected = displayProj.project(projected);
        }
        
       
        
        return projected;
       
    }
    
    public Point lonLatToDisplay (Point p)
    {
        Point projected = new Point(p.x, p.y, p.z);
        if(!(displayProj==null  || displayProj.getID().equals("epsg:4326")))
        {    
        
            projected = displayProj.project(projected);
        }
       
        return projected;
    }
    
    
    
    public void setTilingProj(Projection tilingProj)
    {
        this.tilingProj = tilingProj;
    }
    
    public void setDisplayProj(Projection displayProj)
    {
        this.displayProj = displayProj;
    }
    
    public Projection getTilingProj()
    {
        return tilingProj;
    }
    
    public Projection getDisplayProj()
    {
        return displayProj;
    }
}
