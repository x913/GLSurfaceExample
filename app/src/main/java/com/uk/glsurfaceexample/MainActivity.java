package com.uk.glsurfaceexample;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.logging.Logger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {
    private int mProgram, mBlurProgram;
    private Camera.Parameters mParams;

    private FloatBuffer textureVerticesBuffer;
    private ShortBuffer drawListBuffer;

    private static final String TAG = "AAA";
    static Camera mCamera = null;
    int mTextureID = -1;
    private int cameraId;
    private GLSurfaceView mGLSurfaceView;
    private SurfaceTexture mSurfaceTexture;

    private int muMVPMatrixHandle, mBlurMVPMatrixHandle;
    private int muSTMatrixHandle, mBlurSTMatrixHandle;
    private int maPositionHandle, mBlurPositionHandle;
    private int maTextureHandle, mBlurTextureHandle;

    private float[] mMVPMatrix = new float[16];
    private short drawOrder[] = { 0, 1, 2, 0, 2, 3 }; // order to draw vertices

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private static final int SAMPLE_WIDTH = 64;
    private static final int SAMPLE_HEIGHT = 32;


    private FloatBuffer mTriangleVertices, mTextureBuffer, mVerticesBuffer, mOffsetVerticesBuffer;

    private final float[] mTriangleVerticesData = {
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0, 0.f, 0.f,
            1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f, 1.0f, 0, 0.f, 1.f,
            1.0f, 1.0f, 0, 1.f, 1.f,
    };
    private final float[] mTextureData = {
            // U, V
            0.f, 0.f,
            1.f, 0.f,
            0.f, 1.f,
            1.f, 1.f,
    };
    private final float[] mVerticesData = {
            // X, Y, Z
            -1.0f, -1.0f, 0,
            1.0f, -1.0f, 0,
            -1.0f, 1.0f, 0,
            1.0f, 1.0f, 0,
    };

    static float textureVertices[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,
    };

    private int filters[] = {
            R.raw.normal_frag, R.raw.lomo_frag, R.raw.feather_frag, R.raw.old_frag, R.raw.lomo_frag,
            R.raw.softness_frag, R.raw.cartoon_frag, R.raw.invert_frag
    };

    private int mFilter = 0;
    private boolean mUpdateShader = true;


    private void initFilter() {

        String fragmentShader = ShaderUtil.getShaderSource(MainActivity.this, filters[mFilter]);
        String vertexShader = ShaderUtil.getShaderSource(MainActivity.this, R.raw.camera_vertex);
        initShader(vertexShader, fragmentShader);
    }


    public void initShader(String vertexShader, String fragmentShader) {

        mProgram = ShaderUtil.createProgram(vertexShader, fragmentShader);

        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        ShaderUtil.checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        ShaderUtil.checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        //uMVPMatrix, uSTMatrix is defined in file "vertext.sh", is uniform mat4 type.

        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        ShaderUtil.checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        ShaderUtil.checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }

    }

    private void initBuffer()
    {

        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(textureVertices.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVerticesBuffer = bb2.asFloatBuffer();
        textureVerticesBuffer.put(textureVertices);
        textureVerticesBuffer.position(0);

        // initialize float buffer mTriangleVertices
        mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);

    }


    public void draw(float[] mtx) {

        GLES20.glUseProgram(mProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

        //赋值给Attribute aPosition
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        ShaderUtil.checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        ShaderUtil.checkGlError("glEnableVertexAttribArray maPositionHandle");

        //赋值给Attribute aTextureCoord
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(maTextureHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        //TODO: 旋转

			/*
  			  textureVerticesBuffer.clear();
  			  textureVerticesBuffer.put(transformTextureCoordinates(textureVertices, mtx));
   			  textureVerticesBuffer.position(0);
    		  GLES20.glVertexAttribPointer(maTextureHandle, 2,
              GLES20.GL_FLOAT, false, 2*4, textureVerticesBuffer);
            */
        ShaderUtil.checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        ShaderUtil.checkGlError("glEnableVertexAttribArray maTextureHandle");


        //GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        Matrix.setIdentityM(mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mtx, 0);

        GLES20.glBlendColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        ShaderUtil.checkGlError("glDrawArrays");

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
    }

    private void initCamera(float previewRate) {
        mParams = mCamera.getParameters();
        mParams.setPictureFormat(ImageFormat.JPEG);

        Camera.Size pictureSize = CamParaUtil.getInstance().getPropPictureSize(
                mParams.getSupportedPictureSizes(),previewRate, 800);
        mParams.setPictureSize(pictureSize.width, pictureSize.height);
        Camera.Size previewSize = CamParaUtil.getInstance().getPropPreviewSize(
                mParams.getSupportedPreviewSizes(), previewRate, 800);
        mParams.setPreviewSize(previewSize.width, previewSize.height);
        mCamera.setDisplayOrientation(90);

        List<String> focusModes = mParams.getSupportedFocusModes();
        if(focusModes.contains("continuous-video")){
            mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        mCamera.setParameters(mParams);
        try {
            mCamera.startPreview();
            Log.d(TAG, "Start camera preview is OK");
        }catch (Exception ex) {
            Log.d(TAG, "exception while initCamera " + ex.getMessage());
        }
    }

    public void doStartPreview(SurfaceHolder holder, float previewRate) {
        if(mCamera != null){
            try {
                //mCamera.setPreviewDisplay(holder);
                mCamera.setPreviewTexture(mSurfaceTexture);
                Log.d(TAG, "doStartPreview setPreviewDisplay is OK");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            initCamera(previewRate);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGLSurfaceView = new GLSurfaceView(this);
        mGLSurfaceView.setEGLContextClientVersion(2);

        mGLSurfaceView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                //GLES20.glClearColor(0.5f,0.5f,0.5f, 1.0f);
                Log.i(TAG, "onSurfaceCreated...");
                mTextureID = createTextureID();
                mSurfaceTexture = new SurfaceTexture(mTextureID);
                initBuffer();

                // CameraInterface.getInstance().doOpenCamera(null);
                // open camera here
                mCamera = Camera.open(findFrontFacingCamera());
                Log.d(TAG, "Camera open");

                mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        Log.v(TAG, "onFrameAvailable...");
                        if(mGLSurfaceView != null)
                            mGLSurfaceView.requestRender();

                    }
                });
            }

            @Override
            public void onSurfaceChanged(GL10 gl10, int width, int height) {
                Log.d(TAG, "onSurfaceChanged");
                GLES20.glViewport(0, 0, width, height);
                //CameraInterface.getInstance().doStartPreview(mSurface, 1.77f);
                if(mCamera != null) {
                    Log.d(TAG, "Starting preview onSurfaceChanged");
                    doStartPreview(mGLSurfaceView.getHolder(), 1.77f);
                }
            }

            @Override
            public void onDrawFrame(GL10 gl10) {
                //GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
                if(mSurfaceTexture != null) {

                    if (mUpdateShader) {
                        initFilter();
                        mUpdateShader = false;
                    }


                    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                    mSurfaceTexture.updateTexImage();
                    float[] mtx = new float[16];
                    mSurfaceTexture.getTransformMatrix(mtx);
                    draw(mtx);
                }
            }
        });

        setContentView(mGLSurfaceView);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // do we have a camera?
        if (!getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Toast.makeText(this, "No camera on this device", Toast.LENGTH_LONG)
                    .show();
        } else {
            cameraId = findFrontFacingCamera();
            if (cameraId < 0) {
                Toast.makeText(this, "No front facing camera found.",
                        Toast.LENGTH_LONG).show();
            } else {
//                mCamera = Camera.open(cameraId);
//                Camera.Parameters parameters = mCamera.getParameters();
//                Log.d(TAG, parameters.getFocusMode());
//                // added
//                parameters.set("orientation", "portrait");
////                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
//                parameters.setPreviewSize(1280, 720);
//                mCamera.setDisplayOrientation(90);
//                mCamera.setParameters(parameters);
//
//                //parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//                //mCamera.setParameters(parameters);
//                mCamera.startPreview();
//
//                Toast.makeText(this, "Camera found, opening and start preview",
//                        Toast.LENGTH_LONG).show();
//                if(initSurfaceTexture()) {
//                    Log.d(TAG, "initSurfaceTexture SUCCESS");
//                }
            }
        }

        RequestUserPermission requestUserPermission = new RequestUserPermission(this);
        requestUserPermission.verifyStoragePermissions();
    }

    private int createTextureID()
    {
        int[] texture = new int[1];

        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return texture[0];
    }



    private int findFrontFacingCamera() {
        Log.d(TAG, "Finding front facing cam");
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.d(TAG, "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission is not granted");
        } else {
            Log.d(TAG, "Permission granted");//        mCamera = Camera.open();
            //int cameraId = findFrontFacingCamera();
            Log.d(TAG, "CameraID " + cameraId);
//            mCamera = Camera.open(cameraId);
//            Camera.Parameters parameters = mCamera.getParameters();
//            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//            mCamera.setParameters(parameters);
//            mCamera.startPreview();
        }
    }
}