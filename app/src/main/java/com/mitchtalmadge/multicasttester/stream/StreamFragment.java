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
import android.os.Bundle;
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

import java.util.Collections;
import java.util.Objects;

public class StreamFragment extends Fragment implements View.OnClickListener {

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
    private CameraDevice cameraDevice;
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
        if (!this.isStreaming) {
            this.isStreaming = true;
            this.startStreamingButton.setText(R.string.stop_streaming);
            this.startStreamingButton.setIcon(ContextCompat.getDrawable(Objects.requireNonNull(getActivity()).getApplicationContext(), R.drawable.ic_stream_stop));
        }
    }

    private void stopStreaming() {
        if (this.isStreaming) {
            this.isStreaming = false;
            this.startStreamingButton.setText(R.string.start_streaming);
            this.startStreamingButton.setIcon(ContextCompat.getDrawable(Objects.requireNonNull(getActivity()).getApplicationContext(), R.drawable.ic_stream));
        }
    }

    /**
     * Attempts to open the camera and later display a preview.
     *
     * @param requestPermission Whether to request permission if needed.
     * @return Whether the camera was opened.
     */
    private boolean openCamera(boolean requestPermission) {
        // Don't open again if already open.
        if (cameraDevice != null)
            return true;

        // We need a texture to use the camera.
        if (!cameraTextureAvailable)
            return false;

        // Check permission and request if needed.
        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(getContext()), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            displayCameraPermissionInstructions();
            if (requestPermission)
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA_ID);
            return false;
        }

        CameraManager cameraManager = (CameraManager) Objects.requireNonNull(getActivity()).getSystemService(Context.CAMERA_SERVICE);

        try {
            String cameraId = Objects.requireNonNull(cameraManager).getCameraIdList()[0];
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            int displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            swapPreviewDimensions = false;
            switch(displayRotation) {
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

            cameraManager.openCamera(cameraId, new CameraStateCallback(), null);

            return true;
        } catch (CameraAccessException e) {
            Log.e(getClass().getName(), "Could not open camera", e);
            displayCameraErrorMessage(e.getMessage());
            closeCamera();
            return false;
        }
    }

    private void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
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
        if(swapPreviewDimensions) {
            int tempD = resW;
            resW = resH;
            resH = tempD;
        }

        Log.v(getClass().getName(), "Scaling with camera dimensions (" + resW + ", " + resH + ")");
        int displayW;
        int displayH;
        if (resW > resH) {
            displayW = cameraRegion.getWidth();
            displayH = cameraRegion.getWidth() / (resW / resH);
        } else {
            displayW = cameraRegion.getHeight() / (resH / resW);
            displayH = cameraRegion.getHeight();
        }
        Log.v(getClass().getName(), "Setting preview dimensions to (" + displayW + ", " + displayH + ")");
        cameraTexture.setAspectRatio(displayW, displayH);
    }

    private void createCameraPreview() {
        if(cameraDevice == null)
            return;

        displayCameraPreview();
        try {
            scaleCameraPreview(cameraOutputSize.getWidth(), cameraOutputSize.getHeight());
            SurfaceTexture cameraSurfaceTexture = cameraTexture.getSurfaceTexture();
            cameraSurfaceTexture.setDefaultBufferSize(cameraOutputSize.getWidth(), cameraOutputSize.getHeight());
            Surface cameraSurface = new Surface(cameraSurfaceTexture);

            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(cameraSurface);
            cameraDevice.createCaptureSession(Collections.singletonList(cameraSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null) {
                        return;
                    }

                    updateCameraPreview(cameraCaptureSession, captureRequestBuilder);
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(getClass().getName(), "Camera capture session configuration failed");
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(getClass().getName(), "Could not create camera preview", e);
            displayCameraErrorMessage(e.getMessage());
            closeCamera();
        }
    }

    private void updateCameraPreview(CameraCaptureSession cameraCaptureSession, CaptureRequest.Builder captureRequestBuilder) {
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(getClass().getName(), "Camera preview update failed", e);
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
            displayCameraErrorMessage("Camera state errored with code " + i);
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
            closeCamera();
            openCamera(false);
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
