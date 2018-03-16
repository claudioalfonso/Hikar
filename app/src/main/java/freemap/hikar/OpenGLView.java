package freemap.hikar;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.content.Context;
import android.os.AsyncTask;
import android.opengl.GLES20;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.ArrayList;

import freemap.data.Way;
import freemap.data.Point;
import freemap.datasource.FreemapDataset;
import freemap.jdem.DEM;
import freemap.datasource.Tile;


public class OpenGLView extends GLSurfaceView {

    DataRenderer renderer;

    static class SetRenderTask extends AsyncTask<DownloadDataTask.ReceivedData, Void, Boolean> {

        WeakReference<DataRenderer> rendererRef;

        public SetRenderTask(DataRenderer renderer) {
            rendererRef = new WeakReference<DataRenderer>(renderer);
        }

        protected Boolean doInBackground(DownloadDataTask.ReceivedData... d) {
            DataRenderer renderer = rendererRef.get();

            if (renderer != null) {

                int i = 0;
                renderer.nrw = 0;
                renderer.loadingData = true;


                if (d[0].dem != null) {

                    if (renderer.renderedDEMs == null) // do not clear out when we enter a new tile!
                        renderer.renderedDEMs = new HashMap<String, RenderedDEM>();
                    synchronized (renderer.renderedDEMs) {
                        for (HashMap.Entry<String, Tile> entry : d[0].dem.entrySet()) {
                            DEM curDEM = (DEM) entry.getValue().data;

                            String key = entry.getKey();

                            if (renderer.renderedDEMs.get(key) == null)
                                renderer.renderedDEMs.put(key, new RenderedDEM(curDEM, renderer.trans));

                            i++;

                        }
                    }

                }

                if (renderer.renderedWays == null) // do not clear out when we enter a new tile!
                    renderer.renderedWays = new ArrayList<RenderedWay>();
                if (d[0].osm != null) {

                    i = 0;
                    for (HashMap.Entry<String, Tile> entry : d[0].osm.entrySet()) {

                        // We don't want to have to operate on a tile we've already dealt with
                        if (renderer.receivedDatasets.get(entry.getKey()) == null) {

                            FreemapDataset curOSM = (FreemapDataset) entry.getValue().data;
                            renderer.receivedDatasets.put(entry.getKey(), curOSM);
                            curOSM.operateOnWays(renderer);

                        }
                    }

                }

                return true;
            }
            return false;
        }

        public void onPostExecute(Boolean result) {
            DataRenderer renderer = rendererRef.get();
            if (renderer != null) {
                renderer.loadingData = false;
                Message m = new Message();
                Bundle bundle = new Bundle();
                bundle.putBoolean("finishedData", true);
                m.setData(bundle);
                renderer.openGLViewStatusHandler.sendMessage(m);
            }
        }
    }

    class DataRenderer implements GLSurfaceView.Renderer, FreemapDataset.WayVisitor {


        float hFov;
        float[] modelviewMtx, perspectiveMtx;
        Handler openGLViewStatusHandler;
        boolean loadingData;

        // 180215 replace HashMap of renderedWays with array list.
        // There is now no need to index the rendered ways by id (idea was duplicate prevention), as we are
        // using tiled FreemapDatasets in a HashMap indexed by bottom left coords and ways are split at tile boundaries.
        // Furthermore having one HashMap spanning all FreemapDatasets (as was) will lead to the problem
        // in which we have 2 ways (one split way) with the same ID across different tiles meaning that if
        // we use a HashMap with the ID as the index, only one will be added
        //HashMap<Long,RenderedWay> renderedWays;
        ArrayList<RenderedWay> renderedWays;
        HashMap<String, RenderedDEM> renderedDEMs;
        HashMap<String, FreemapDataset> receivedDatasets;
        boolean calibrate;
        GLRect calibrateRect, cameraRect;
        float zDisp;
        Point cameraPos;
        GPUInterface gpuInterface, textureInterface;
        float[] texcoords;
        int textureId;
        SurfaceTexture cameraFeed;
        CameraCapturer cameraCapturer;
        TileDisplayProjectionTransformation trans;
        float nearPlane = 2.0f, farPlane = 3000.0f;


        int nrw;


        public DataRenderer(Handler openGLViewStatusHandler) {
            hFov = 40.0f;
            renderedWays = new ArrayList<RenderedWay>();
            renderedDEMs = new HashMap<String, RenderedDEM>();
            receivedDatasets = new HashMap<String, FreemapDataset>();
            this.openGLViewStatusHandler = openGLViewStatusHandler;

            zDisp = 1.4f;


            // calibrate with an object 50cm long and 50cm away
            float zDist = 0.5f, xLength = 0.5f;

            calibrateRect = new GLRect(new float[]{xLength / 2, 0.0f, -zDist, -xLength / 2, 0.0f, -zDist,
                    -xLength / 2, 0.05f, -zDist, xLength / 2, 0.05f, -zDist},
                    new float[]{1.0f, 1.0f, 1.0f, 1.0f});


            cameraRect = new GLRect(new float[]{-1.0f, 1.0f, 0.0f,
                    -1.0f, -1.0f, 0.0f,
                    1.0f, -1.0f, 0.0f,
                    1.0f, 1.0f, 0.0f}, null);


            modelviewMtx = new float[16];
            perspectiveMtx = new float[16];
            Matrix.setIdentityM(modelviewMtx, 0);
            Matrix.setIdentityM(perspectiveMtx, 0);

            trans = null; //new TileDisplayProjectionTransformation (IdentityProjection.getInstance(),IdentityProjection.getInstance());


        }

        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            GLES20.glClearColor(0.0f, 0.0f, 0.3f, 0.0f);
            GLES20.glClearDepthf(1.0f);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            final String vertexShader =
                    "attribute vec4 aVertex;\n" +
                            "uniform mat4 uPerspMtx, uMvMtx;\n" +
                            "void main(void)\n" +
                            "{\n" +
                            "gl_Position = uPerspMtx * uMvMtx * aVertex;\n" +
                            "}\n",
                    fragmentShader =
                            "precision mediump float;\n" +
                                    "uniform vec4 uColour;\n" +
                                    "void main(void)\n" +
                                    "{\n" +
                                    "gl_FragColor = uColour;\n" +
                                    "}\n";
            gpuInterface = new GPUInterface(vertexShader, fragmentShader);

            // http://stackoverflow.com/questions/6414003/using-surfacetexture-in-android
            final int GL_TEXTURE_EXTERNAL_OES = 0x8d65;
            int[] textureId = new int[1];
            GLES20.glGenTextures(1, textureId, 0);
            if (textureId[0] != 0) {
                GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId[0]);
                GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

                // Must negate y when calculating texcoords from vertex coords as bitmap image data assumes
                // y increases downwards
                final String texVertexShader =
                        "attribute vec4 aVertex;\n" +
                                "varying vec2 vTextureValue;\n" +
                                "void main (void)\n" +
                                "{\n" +
                                "gl_Position = aVertex;\n" +
                                "vTextureValue = vec2(0.5*(1.0 + aVertex.x), 0.5*(1.0-aVertex.y));\n" +
                                "}\n",
                        texFragmentShader =
                                "#extension GL_OES_EGL_image_external: require\n" +
                                        "precision mediump float;\n" +
                                        "varying vec2 vTextureValue;\n" +
                                        "uniform samplerExternalOES uTexture;\n" +
                                        "void main(void)\n" +
                                        "{\n" +
                                        "gl_FragColor = texture2D(uTexture,vTextureValue);\n" +
                                        "}\n";
                textureInterface = new GPUInterface(texVertexShader, texFragmentShader);
                GPUInterface.setupTexture(textureId[0]);
                textureInterface.setUniform1i("uTexture", 0); // this is the on-gpu texture register not the texture id
                cameraFeed = new SurfaceTexture(textureId[0]);
                cameraCapturer = new CameraCapturer(this);
                onResume();
            }
        }

        public void onDrawFrame(GL10 unused) {

            Matrix.setIdentityM(perspectiveMtx, 0);
            float aspectRatio = (float) getWidth() / (float) getHeight();
            Matrix.perspectiveM(perspectiveMtx, 0, hFov / aspectRatio, aspectRatio,
                    nearPlane, farPlane);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            if (cameraCapturer.isActive()) {
                cameraFeed.updateTexImage();
                float[] tm = new float[16];
                cameraFeed.getTransformMatrix(tm);

                textureInterface.select();
                cameraRect.draw(textureInterface);
            }
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            gpuInterface.select();


            if (calibrate) {
                Matrix.setIdentityM(modelviewMtx, 0);
                gpuInterface.sendMatrix(modelviewMtx, "uMvMtx");
                gpuInterface.sendMatrix(perspectiveMtx, "uPerspMtx");
                calibrateRect.draw(gpuInterface);
            } else if (!loadingData) // don't attempt to render anything while loading
            {

                //Matrix.translateM(modelviewMtx, 0, 0, 0, -zDisp); // needed????


                // Prevent the ConcurrentModificationException, This is supposed to happen because you're
                // adding to the renderedDEMs while iterating through them, and you can't add to a 
                // collection at the same time as iterating through it
                // don't try to do this if loading data otherwise the synchronized blockwill prevent
                // access to renderedDEMs when loading data

                synchronized (renderedDEMs) {

                    for (HashMap.Entry<String, RenderedDEM> d : renderedDEMs.entrySet()) {
                        if (d.getValue().centreDistanceTo(cameraPos) < 5000.0)
                            d.getValue().render(gpuInterface);
                    }

                }

                if (renderedWays.size() > 0) {


                    // NOTE! The result matrix must not refer to the same array in memory as either
                    // input matrix! Otherwise you get strange results.
                    /* we now start with the orientation matrix from the sensors so no need for this anyway
                    if(this.modelviewMtx!=null) 
                        Matrix.multiplyMM(modelviewMtx, 0, modelviewMtx, 0, modelviewMtx, 0);
                    */

                    Matrix.translateM(modelviewMtx, 0, (float) -cameraPos.x,
                            (float) -cameraPos.y, (float) (-cameraPos.z - zDisp));

                    gpuInterface.sendMatrix(modelviewMtx, "uMvMtx");
                    gpuInterface.sendMatrix(perspectiveMtx, "uPerspMtx");


                    synchronized (renderedWays) {
                        int rWayCount = 0, drawnCount = 0;
                        double avDist = 0.0;
                        for (RenderedWay rWay : renderedWays) {
                            rWayCount++;
                            avDist += rWay.distanceTo(cameraPos);

                            if (rWay.isDisplayed() && rWay.distanceTo(cameraPos) <= farPlane) {
                                rWay.draw(gpuInterface);
                                drawnCount++;
                            }
                        }
                        avDist /= rWayCount;
                    }

                }
            }
        }

        public void onSurfaceChanged(GL10 unused, int width, int height) {
            // need to get the camera parameters
            GLES20.glViewport(0, 0, width, height);
            float aspectRatio = (float) width / (float) height;
            Matrix.setIdentityM(perspectiveMtx, 0);
            Matrix.perspectiveM(perspectiveMtx, 0, hFov / aspectRatio, aspectRatio, nearPlane, farPlane);
        }

        public void onPause() {
            if (cameraCapturer != null) {
                cameraCapturer.releaseCamera();
            }
        }

        public void onResume() {
            if (cameraCapturer != null) {
                cameraCapturer.openCamera();
                float camHfov = cameraCapturer.getHFOV();
                if (camHfov > 0.0f) {
                    setHFOV(camHfov);
                    Message m = new Message();
                    Bundle bundle = new Bundle();
                    bundle.putFloat("hfov", camHfov);
                    m.setData(bundle);
                    renderer.openGLViewStatusHandler.sendMessage(m);
                }
                try {
                    cameraCapturer.startPreview(cameraFeed);
                } catch (IOException e) {
                    cameraCapturer.releaseCamera();
                }
            }
        }

        public void setOrientMtx(float[] orientMtx) {
            this.modelviewMtx = orientMtx.clone();
        }

        public void setRenderData(DownloadDataTask.ReceivedData data) {


            if (trans != null) {

                SetRenderTask setRenderDataTask = new SetRenderTask(this);
                setRenderDataTask.execute(data);
            }
        }

        public void setCameraLocation(Point unprojected) {
            if (trans != null) {
                cameraPos = trans.lonLatToDisplay(unprojected);
            }
        }

        public void setHeight(double height) {
            cameraPos.z = height;
        }

        public void visit(Way w) {

            synchronized (renderedWays) {
                if (trans != null) {
                    renderedWays.add(new RenderedWay(w, 2.0f, trans));
                }
            }

        }

        // setRotation() removed as duplicates setOrientMtx()
        // 100318 always send message back in setHFOV()

        public void setHFOV(float hFov) {
            this.hFov = hFov;

        }

        public void changeHFOV(float amount) {
            setHFOV(this.hFov + amount);
        }

        public void setCameraHeight(float cameraHeight) {
            zDisp = cameraHeight;
        }

        public void setProjectionTransformation(TileDisplayProjectionTransformation trans) {
            this.trans = trans;
        }

        public void deactivate() {
            renderedWays = new ArrayList<RenderedWay>();
            renderedDEMs = new HashMap<String, RenderedDEM>();
        }

    }

    public OpenGLView(Context ctx, Hikar.OpenGLViewStatusHandler handler) {
        super(ctx);

        setEGLContextClientVersion(2);
        renderer = new DataRenderer(handler);
        setRenderer(renderer);

    }

    public DataRenderer getRenderer() {
        return renderer;
    }
}