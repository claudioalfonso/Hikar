package freemap.hikar;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteOrder;

import freemap.jdem.DEM;
import freemap.data.Point;

import android.opengl.GLES20;

import android.util.Log;

public class RenderedDEM {

    FloatBuffer vertexBuffer;
    ShortBuffer indexBuffer;
    float[] surfaceColour = { 0.0f, 1.0f, 0.0f, 0.0f };
    
    Point centrePoint;
    
  
    public RenderedDEM (DEM dem, TileDisplayProjectionTransformation trans)
    {
        int nrows = dem.getPtHeight(), ncols = dem.getPtWidth(), nvertices = nrows*ncols;
        
        ByteBuffer buf = ByteBuffer.allocateDirect(nvertices*3*4);
        buf.order(ByteOrder.nativeOrder());
        vertexBuffer = buf.asFloatBuffer();

        // triangle strip with degenerate triangles to allow multiple lines
        int nIndices =  (ncols*2 +2) *(nrows-1) - 2;
        
        ByteBuffer ibuf = ByteBuffer.allocateDirect( nIndices*2 );
        ibuf.order(ByteOrder.nativeOrder());
        indexBuffer = ibuf.asShortBuffer();
        
        Point bottomLeft = trans.tileToDisplay(dem.getBottomLeft()), topRight = trans.tileToDisplay(dem.getTopRight());
        
        centrePoint = new Point((bottomLeft.x + topRight.x) / 2,
                                (bottomLeft.y + topRight.y) / 2);
        
       
       
        for(int row=0; row<nrows; row++)
        {
            for(int col=0; col<ncols; col++)
            {
                Point p = trans.tileToDisplay(dem.getPoint(col, row));
            
                vertexBuffer.put( (float)p.x);
                vertexBuffer.put( (float)p.y);
                vertexBuffer.put( (float)(p.z-5));
                
            }
        }
         
        vertexBuffer.position(0);
        
        short[] indices = new short[nIndices];
        String istr = "";
        
        int i=0;
        for(int row=0; row<nrows-1; row++)  
        {
            for(int col=0; col<ncols; col++)
            {
                indices[i++] = (short)(row*ncols + col);
                if(row<3 || row==nrows-2)
                    istr += indices[i-1] + ",";
               
                indices[i++] = (short)(indices[i-2] + ncols);
                
                if(row<3 || row==nrows-2)
                    istr += indices[i-1] + ",";
               
            }
            
          
            
            // degenerate triangles
            if(row<nrows-2)
            {
                indices[i++] = indices[i-2];
                
                if(row<3)
                    istr += indices[i-1] + ",";
                indices[i++] = (short)(indices[i-2]  - ncols + 1);
               
                if(row<3)
                    istr += indices[i-1] + ",";
               
            }
        
            if(row<3)
                istr+="\n";
        } 
        
        //Log.d("hikar","indices (first 3 rows and last row): " + istr);
        
        
        indexBuffer.put(indices);
        indexBuffer.position(0);
    }
    
    
    public void render(GPUInterface gpu)
    {
        gpu.setUniform4fv("uColour", surfaceColour);
        gpu.drawBufferedData(vertexBuffer, indexBuffer, 12, "aVertex", GLES20.GL_TRIANGLE_STRIP);
    }
    
    public double centreDistanceTo(Point p)
    {
        return centrePoint.distanceTo(p);
    }
}
