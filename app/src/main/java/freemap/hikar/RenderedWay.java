package freemap.hikar;

import freemap.data.Point;
import freemap.data.Projection;
import freemap.data.Way;
import freemap.data.IdentityProjection;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.ArrayList;

public class RenderedWay {

	FloatBuffer vertexBuffer;
	ShortBuffer indexBuffer;
	float[] colour;
	static HashMap<String,float[]> colours;
	short[] indices;
	float[] vertices;
	static float[] road, path, bridleway, track, cycleway, byway;
	boolean valid;
	
	boolean displayed;
    boolean[] vtxDisplayStatus;
    float[] wayVertices; // for line-of-sight tests, saves unnecessary computation
    
	static {
		colours = new HashMap<String,float[]>();
		road = new float[] { 1.0f, 1.0f, 1.0f, 1.0f };
		path = new float[] { 0.0f, 1.0f, 0.0f, 1.0f };
		bridleway = new float[] { 0.67f, 0.33f, 0.0f, 1.0f };
		track = new float[] { 1.0f, 0.5f, 0.0f, 1.0f };
		cycleway = new float[] { 0.0f, 0.0f, 1.0f, 1.0f };
		byway = new float[] { 1.0f, 0.0f, 0.0f, 1.0f };
		colours.put("designation:public_footpath", path );
		colours.put("designation:public_bridleway", bridleway );
		colours.put("designation:public_byway", byway);
		colours.put("designation:restricted_byway", byway );
		colours.put("designation:byway_open_to_all_traffic", byway );
		colours.put("highway:footway", path );
		colours.put("highway:path", path );
		colours.put("highway:bridleway", bridleway );
		colours.put("highway:track", track );
		colours.put("highway:cycleway", cycleway );
	}
	
	
	
	public RenderedWay(Way w,float width,TileDisplayProjectionTransformation trans)
	{
		float dx, dy, dxperp=0.0f, dyperp=0.0f, len;
		
		
		ArrayList<Integer> includedPoints = new ArrayList<Integer>();
		for(int i=0; i<w.nPoints(); i++)
		    if(w.getPoint(i).z >= -0.9) //NW 280614 delete this check to see if this is causing problems, 240215 undelete again should be ok
		        includedPoints.add(i);
		    
		
		
		int nPts = includedPoints.size();
		
		if(nPts==0)
		    return;
		
		
		ByteBuffer buf = ByteBuffer.allocateDirect(nPts*6*4);
		buf.order(ByteOrder.nativeOrder());
		vertexBuffer = buf.asFloatBuffer();
		
		ByteBuffer ibuf = ByteBuffer.allocateDirect((nPts-1)*6*2);
		ibuf.order(ByteOrder.nativeOrder());
		indexBuffer = ibuf.asShortBuffer();
		
		vertices = new float[nPts*6];
		indices = new short[(nPts-1)*6];
		wayVertices = new float[nPts*3];
		vtxDisplayStatus = new boolean[nPts];
		
		displayed = true;
		
		Point thisPoint;
	
		
		
		for(int i=0; i<nPts-1; i++)
		{
		
		       thisPoint = trans.tileToDisplay(w.getPoint(includedPoints.get(i)));
		       Point nextPoint = trans.tileToDisplay(w.getPoint(includedPoints.get(i+1)));
		      
		       
		        dx=(float)(nextPoint.x - thisPoint.x);
		        dy=(float)(nextPoint.y - thisPoint.y);
		        len=(float)thisPoint.distanceTo(nextPoint);
		        
		        dxperp = -(dy*(width/2))/len;
		        dyperp = (dx*(width/2))/len;
		        vertices[i*6] = (float)(thisPoint.x+dxperp);
		        vertices[i*6+1] = (float)(thisPoint.y+dyperp);
		        vertices[i*6+2] = (float)thisPoint.z;
		        vertices[i*6+3] = (float)(thisPoint.x-dxperp);
		        vertices[i*6+4] = (float)(thisPoint.y-dyperp);
		        vertices[i*6+5] = (float)thisPoint.z;
			
			
		        wayVertices[i*3] = (float)thisPoint.x;
		        wayVertices[i*3+1] = (float)thisPoint.y;
		        wayVertices[i*3+2] = (float)thisPoint.z;
		        vtxDisplayStatus[i] = true;
		        
		       
		        /*
			    for(int j=0; j<6; j++)
				    System.out.println("Vertex : " + (i*6+j)+ " position:" +vertices[i*6+j]);
		        */
		   
		}
		int k=nPts-1;
		thisPoint = trans.tileToDisplay(w.getPoint(includedPoints.get(k)));
		vertices[k*6] = (float)(thisPoint.x+dxperp);
		vertices[k*6+1] = (float)(thisPoint.y+dyperp);
		vertices[k*6+2] = (float)thisPoint.z;
		vertices[k*6+3] = (float)(thisPoint.x-dxperp);
		vertices[k*6+4] = (float)(thisPoint.y-dyperp);
		vertices[k*6+5] = (float)thisPoint.z;
		wayVertices[k*3] = (float)thisPoint.x;
        wayVertices[k*3+1] = (float)thisPoint.y;
        wayVertices[k*3+2] = (float)thisPoint.z;
        
		vtxDisplayStatus[k]= true;
		for(int i=0; i<nPts-1; i++)
		{
			indices[i*6] = (short)(i*2);
			indices[i*6+1] = (short)(i*2 + 1);
			indices[i*6+2] = (short)(i*2 + 2);
			indices[i*6+3] = (short)(i*2 + 1);
			indices[i*6+4] = (short)(i*2 + 3);
			indices[i*6+5] = (short)(i*2 + 2);
		}
		
		vertexBuffer.put(vertices);
		indexBuffer.put(indices);
		vertexBuffer.position(0);
		indexBuffer.position(0);
		
		String highway = w.getValue("highway"), designation = w.getValue("designation");
		if(designation!=null && colours.get("designation:"+designation)!=null)
			colour=colours.get("designation:"+designation);
		else //if(highway!=null)
		{
			if(colours.get("highway:"+highway)!=null)
				colour=colours.get("highway:"+highway);
			else
				colour = road;
		}
		valid=true;
	}
	
	public void draw(GPUInterface gpuInterface)
	{
	    
		if(valid && colour!=null)
		{
		    gpuInterface.setUniform4fv("uColour", colour);
			gpuInterface.drawBufferedData(vertexBuffer, indexBuffer, 12, "aVertex");
			
			/* commented out already in gles 1.0 version
			gl.glFrontFace(GL10.GL_CCW);
			gl.glEnable(GL10.GL_CULL_FACE);
			gl.glCullFace(GL10.GL_BACK);
			*/
			
			/* old gles 1.0 stuff
			
			gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
			gl.glVertexPointer(3,GL10.GL_FLOAT,0,vertexBuffer);
			gl.glDrawElements(GL10.GL_TRIANGLES,indices.length,GL10.GL_UNSIGNED_SHORT,indexBuffer);
			
			gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
			gl.glDisable(GL10.GL_CULL_FACE);
			*/
		}
	}

	Point getAveragePosition()
	{
	    if(valid)
	    {
	        Point p=new Point();
	        int nPoints=vertexBuffer.limit() / 3;
	        for(int i=0; i<vertexBuffer.limit(); i+=3)
	        {
	            p.x += vertexBuffer.get(i);
	            p.y += vertexBuffer.get(i+1);
			
	        }
	        p.x /= nPoints;
	        p.y /= nPoints;
		
	        return p;
	    }
	    return null;
	}
	
	public double distanceTo(Point p)
	{
		Point av = getAveragePosition();
		if(av!=null)
		    return av.distanceTo(p);
		return -1.0;
	}
	
	// NEW
	
	public boolean isDisplayed()
    {
        return displayed;
    }
    
    public void setDisplayed(boolean display)
    {
        this.displayed = display;
    }
	
	public void setVtxDisplayStatus(int i,boolean visible)
    {
        vtxDisplayStatus[i] = visible;
    }
    
    // attempt at hiding parts of the way not in line-of-sight
    // not sure if this will slow things up horribly
    // best call infrequently
    public void regenerateDisplayedVertexIndices()
    {
        if(valid)
        {
        // remove indices either side of the given vertex
        
           
         
             // vertex n -> indices of subsequent triangles n*6, n*6+1 etc
            indexBuffer.clear();
            indexBuffer.position(0);
            int nVisibles = 0;
            for(int i=0; i<vtxDisplayStatus.length-1; i++)
            {
                // only add indices if the current vertex is visible and the next vertex
                if(vtxDisplayStatus[i] && vtxDisplayStatus[i+1])
                {  
                    indexBuffer.put(indices[i*6]);
                    indexBuffer.put(indices[i*6+1]);
                    indexBuffer.put(indices[i*6+2]);
                    indexBuffer.put(indices[i*6+3]);
                    indexBuffer.put(indices[i*6+4]);
                    indexBuffer.put(indices[i*6+5]);
                    nVisibles++;
                }
            }
        }
    }
    
    public float[] getWayVertices()
    {
        return wayVertices;
    }
    
    public boolean isValid()
    {
        return valid;
    }
  
    // NEW END
}
