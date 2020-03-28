package com.mitchtalmadge.multicasttester.stream;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.SpannableString;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.mitchtalmadge.multicasttester.R;
import com.mitchtalmadge.multicasttester.stream.rtp.MulticastRtpStreamHandler;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StreamFragment extends Fragment implements View.OnClickListener, GetVideoData {

    private final static int PERMISSION_REQUEST_CAMERA_ID = 100;

    private TextView ipField;
    private TextView portField;
    private MaterialButton startStreamingButton;
    private FrameLayout cameraRegion;
    private LinearLayout cameraPlaceholder;
    private LinearLayout cameraPermissionInstructions;
    private LinearLayout cameraErrorDetails;
    private TextView cameraErrorLabel;
    private AutoFitTextureView cameraTexture;

    private boolean cameraTextureAvailable = false;

    private boolean isStreaming = false;
    private MulticastRtpStreamHandler multicastRtpStreamHandler;

    private CameraDevice cameraDevice;
    private VideoEncoder videoEncoder;
    private HandlerThread cameraBackgroundThread;
    private Handler cameraBackgroundHandler;

    private Size cameraOutputSize;
    private boolean swapPreviewDimensions = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stream, container, false);

        ipField = view.findViewById(R.id.ipField);
        portField = view.findViewById(R.id.portField);
        startStreamingButton = view.findViewById(R.id.startStreamingButton);
        cameraRegion = view.findViewById(R.id.cameraRegion);
        cameraPlaceholder = view.findViewById(R.id.cameraPlaceholder);
        cameraPermissionInstructions = view.findViewById(R.id.cameraPermissionInstructions);
        cameraErrorDetails = view.findViewById(R.id.cameraErrorDetails);
        cameraErrorLabel = view.findViewById(R.id.cameraErrorLabel);
        cameraTexture = view.findViewById(R.id.cameraTexture);

        multicastRtpStreamHandler = new MulticastRtpStreamHandler();

        startStreamingButton.setOnClickListener(this);
        cameraTexture.setSurfaceTextureListener(new CameraSurfaceTextureListener());

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        displayCameraPreview();
    }

    @Override
    public void onPause() {
        super.onPause();

        stopStreaming();
        closeCamera();
    }

    @Override
    public void onResume() {
        super.onResume();

        openCamera(false);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        stopStreaming();
        closeCamera();
    }

    private void startStreaming() {
        Log.v(getClass().getName(), "Starting streaming...");
        if (isStreaming)
            return;

        multicastRtpStreamHandler.connect(ipField.getText().toString(), portField.getText().toString(), getContext());

        isStreaming = true;
        startStreamingButton.setText(R.string.stop_streaming);
        startStreamingButton.setIcon(ContextCompat.getDrawable(Objects.requireNonNull(getActivity()).getApplicationContext(), R.drawable.ic_stream_stop));
    }

    private void stopStreaming() {
        Log.v(getClass().getName(), "Stopping streaming...");
        if (!isStreaming)
            return;

        multicastRtpStreamHandler.disconnect();

        isStreaming = false;
        startStreamingButton.setText(R.string.start_streaming);
        startStreamingButton.setIcon(ContextCompat.getDrawable(Objects.requireNonNull(getActivity()).getApplicationContext(), R.drawable.ic_stream));
    }

    private void startCameraBackgroundThread() {
        cameraBackgroundThread = new HandlerThread("CameraBackground");
        cameraBackgroundThread.start();
        cameraBackgroundHandler = new Handler(cameraBackgroundThread.getLooper());
    }

    private void stopCameraBackgroundThread() {
        cameraBackgroundThread.quitSafely();
        try {
            cameraBackgroundThread.join();
        } catch (InterruptedException e) {
            Log.e(getClass().getName(), "Interrupted while stopping camera background thread.", e);
        }

        cameraBackgroundThread = null;
        cameraBackgroundHandler = null;
    }

    /**
     * Attempts to open the camera and later display a preview.
     *
     * @param requestPermission Whether to request permission if needed.
     * @return Whether the camera was opened.
     */
    private boolean openCamera(boolean requestPermission) {
        Log.v(getClass().getName(), "Opening camera...");

        // Don't open again if already open.
        if (cameraDevice != null) {
            Log.v(getClass().getName(), "Camera already open.");
            return true;
        }

        // We need a texture to use the camera.
        if (!cameraTextureAvailable) {
            Log.v(getClass().getName(), "Camera texture not available.");
            return false;
        }

        // Check permission and request if needed.
        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(getContext()), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.v(getClass().getName(), "Camera permissions not granted yet.");
            displayCameraPermissionInstructions();
            if (requestPermission)
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA_ID);
            return false;
        }

        startCameraBackgroundThread();
        CameraManager cameraManager = (CameraManager) Objects.requireNonNull(getActivity()).getSystemService(Context.CAMERA_SERVICE);

        try {
            String cameraId = Objects.requireNonNull(cameraManager).getCameraIdList()[0];
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            int displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            int sensorOrientation = Objects.requireNonNull(cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));

            swapPreviewDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swapPreviewDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swapPreviewDimensions = true;
                    }
                    break;
            }

            cameraOutputSize = Objects.requireNonNull(streamConfigurationMap).getOutputSizes(SurfaceTexture.class)[0];

            videoEncoder = new VideoEncoder(this);
            videoEncoder.prepareVideoEncoder(
                    1280,
                    720,
                    30,
                    2_000,
                    CameraHelper.getCameraOrientation(getContext()),
                    swapPreviewDimensions,
                    2,
                    FormatVideoEncoder.SURFACE);

            cameraManager.openCamera(cameraId, new CameraStateCallback(), cameraBackgroundHandler);

            return true;
        } catch (CameraAccessException e) {
            Log.e(getClass().getName(), "Could not open camera", e);
            displayCameraErrorMessage(e.getMessage());
            closeCamera();
            return false;
        }
    }

    private void closeCamera() {
        Log.v(getClass().getName(), "Closing camera...");
        if (cameraDevice != null) {
            cameraDevice.close();
            videoEncoder.stop();
            stopCameraBackgroundThread();
            cameraDevice = null;
        }

        stopStreaming();
    }

    private void displayCameraPlaceholder() {
        cameraPlaceholder.setVisibility(View.VISIBLE);
        cameraTexture.setVisibility(View.GONE);

        // Reset
        cameraPermissionInstructions.setVisibility(View.GONE);
        cameraErrorDetails.setVisibility(View.GONE);
    }

    private void displayCameraPermissionInstructions() {
        displayCameraPlaceholder();
        this.cameraPermissionInstructions.setVisibility(View.VISIBLE);
    }

    private void displayCameraErrorMessage(String message) {
        displayCameraPlaceholder();
        this.cameraErrorDetails.setVisibility(View.VISIBLE);
        SpannableString errorMessage = new SpannableString(message);
        errorMessage.setSpan(Typeface.BOLD, 0, errorMessage.length(), 0);
        this.cameraErrorLabel.setText(errorMessage);
    }

    private void displayCameraPreview() {
        cameraTexture.setVisibility(View.VISIBLE);
        cameraPlaceholder.setVisibility(View.GONE);
    }

    /**
     * Scale the camera preview to fit the specified camera resolution without distortion.
     *
     * @param resW The width of the camera resolution.
     * @param resH The height of the camera resolution.
     */
    private void scaleCameraPreview(int resW, int resH) {
        Log.v(getClass().getName(), "Scaling with camera dimensions (" + resW + ", " + resH + ")");
        if (swapPreviewDimensions) {
            Log.v(getClass().getName(), "Using swapped camera dimensions");
            int tempD = resW;
            resW = resH;
            resH = tempD;
        }

        cameraTexture.setAspectRatio(resW, resH);
    }

    private void createCameraPreview() {
        if (cameraDevice == null)
            return;

        displayCameraPreview();
        try {
            Objects.requireNonNull(getActivity()).runOnUiThread(() -> scaleCameraPreview(cameraOutputSize.getWidth(), cameraOutputSize.getHeight()));
            SurfaceTexture cameraSurfaceTexture = cameraTexture.getSurfaceTexture();
            cameraSurfaceTexture.setDefaultBufferSize(cameraOutputSize.getWidth(), cameraOutputSize.getHeight());
            Surface cameraSurface = new Surface(cameraSurfaceTexture);

            cameraBackgroundHandler.post(() -> videoEncoder.start());

            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(cameraSurface);
            captureRequestBuilder.addTarget(videoEncoder.getInputSurface());
            cameraDevice.createCaptureSession(Arrays.asList(cameraSurface, videoEncoder.getInputSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null) {
                        return;
                    }

                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, cameraBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(getClass().getName(), "Could not create camera repeating request", e);
                        Objects.requireNonNull(getActivity()).runOnUiThread(() ->
                                displayCameraErrorMessage(e.getMessage()));
                        closeCamera();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(getClass().getName(), "Camera capture session configuration failed");
                    Objects.requireNonNull(getActivity()).runOnUiThread(() ->
                            displayCameraErrorMessage("Failed to configure camera capture session."));
                    closeCamera();
                }
            }, cameraBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(getClass().getName(), "Could not create camera preview", e);
            displayCameraErrorMessage(e.getMessage());
            closeCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CAMERA_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                displayCameraPlaceholder();
                openCamera(false);
                startStreaming();
            } else {
                stopStreaming();
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.startStreamingButton) {
            if (isStreaming) {
                stopStreaming();
            } else {
                if (openCamera(true)) {
                    startStreaming();
                }
            }
        }
    }

    @Override
    public void onSpsPps(ByteBuffer sps, ByteBuffer pps) {
        multicastRtpStreamHandler.setSpsPps(sps, pps);
    }

    @Override
    public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        multicastRtpStreamHandler.setSpsPps(sps, pps);
    }

    @Override
    public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        if (!isStreaming)
            return;
        multicastRtpStreamHandler.writeH264Data(h264Buffer, info);
    }

    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {

    }

    private class CameraStateCallback extends CameraDevice.StateCallback {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.i(getClass().getName(), "Camera Opened");
            StreamFragment.this.cameraDevice = cameraDevice;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.e(getClass().getName(), "Camera Error Code: " + i);
            Objects.requireNonNull(getActivity()).runOnUiThread(() ->
                    displayCameraErrorMessage("Camera state errored with code " + i));
            closeCamera();
        }
    }

    private class CameraSurfaceTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            Log.v(getClass().getName(), "Preview surface available with size (" + i + ", " + i1 + ")");
            cameraTextureAvailable = true;
            openCamera(false);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            Log.v(getClass().getName(), "Preview surface size changed to (" + i + ", " + i1 + ")");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.v(getClass().getName(), "Preview surface destroyed.");
            cameraTextureAvailable = false;
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    }

}
