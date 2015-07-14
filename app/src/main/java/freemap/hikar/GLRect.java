package freemap.hikar;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteOrder;


public class GLRect {
	
		FloatBuffer vertexBuffer;
		ShortBuffer indexBuffer;	
	
		short[] indices;
		float[] colour;
		
		public GLRect(float[] vertices, float[] colour)
		{	
			ByteBuffer buf = ByteBuffer.allocateDirect(12*4);
			buf.order(ByteOrder.nativeOrder());
			vertexBuffer = buf.asFloatBuffer();
			ByteBuffer ibuf = ByteBuffer.allocateDirect(6*2);
			ibuf.order(ByteOrder.nativeOrder());
			indexBuffer = ibuf.asShortBuffer();
			
			indices = new short[]  {0,1,2,2,3,0};

			
			vertexBuffer.put(vertices);
			indexBuffer.put(indices);
			vertexBuffer.position(0);
			indexBuffer.position(0);
			
			this.colour=colour;
		}
		
		public void draw(GPUInterface gpu)
		{
		    if(colour!=null)
		        gpu.setUniform4fv("uColour", colour);
		    gpu.drawBufferedData(vertexBuffer, indexBuffer, 12, "aVertex");
		    /* old opengl es 1.0 code
				gl.glColor4f(colour[0],colour[1],colour[2],colour[3]);
				
				gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
				gl.glVertexPointer(3,GL10.GL_FLOAT,0,vertexBuffer);
				gl.glDrawElements(GL10.GL_TRIANGLES,indices.length,GL10.GL_UNSIGNED_SHORT,indexBuffer);
				
				gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
				gl.glDisable(GL10.GL_CULL_FACE);
		    */
		    
		}

}
