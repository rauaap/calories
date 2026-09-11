package com.rauaap.calories;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import org.json.JSONException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Scans a product barcode, then finds the food: a known barcode is returned
 * (or opened) directly, otherwise Open Food Facts is asked and the prefilled
 * form opens. When started for a result, the saved food's id is passed back.
 */
public class ScanActivity extends Activity {
    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_EDIT = 2;

    private final MultiFormatReader reader = new MultiFormatReader();
    private final ExecutorService lookups = Executors.newSingleThreadExecutor();
    /** Held while a camera open is in flight, so closing waits for it. */
    private final Semaphore openLock = new Semaphore(1);
    private TextureView preview;
    private TextView status;
    private View torch;
    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CaptureRequest.Builder request;
    private ImageReader frames;
    private Size previewSize;
    private boolean torchOn;
    private boolean askedPermission;
    private volatile boolean found;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        Ui.fitInsets(findViewById(R.id.root));
        preview = findViewById(R.id.scan_preview);
        status = findViewById(R.id.scan_status);
        torch = findViewById(R.id.scan_torch);
        findViewById(R.id.scan_close).setOnClickListener(v -> finish());
        torch.setOnClickListener(v -> {
            torchOn = !torchOn;
            if (handler != null) handler.post(this::repeat);
        });

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                EnumSet.of(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (found) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            start();
        } else if (!askedPermission) {
            askedPermission = true;
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_CAMERA && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "The camera permission is needed to scan barcodes", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        lookups.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EDIT) {
            setResult(resultCode, data);
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // Camera

    private void start() {
        thread = new HandlerThread("scanner");
        thread.start();
        handler = new Handler(thread.getLooper());
        if (preview.isAvailable()) {
            openCamera();
            return;
        }
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                fitPreview();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            }
        });
    }

    private void openCamera() {
        if (handler == null || camera != null) return;
        CameraManager manager = getSystemService(CameraManager.class);
        try {
            String id = backCamera(manager);
            if (id == null) {
                fail("No camera found");
                return;
            }
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewSize = pickSize(map.getOutputSizes(SurfaceTexture.class));
            Size frameSize = pickSize(map.getOutputSizes(ImageFormat.YUV_420_888));
            frames = ImageReader.newInstance(frameSize.getWidth(), frameSize.getHeight(), ImageFormat.YUV_420_888, 2);
            frames.setOnImageAvailableListener(this::onFrame, handler);
            torch.setVisibility(Boolean.TRUE.equals(chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
                    ? View.VISIBLE : View.GONE);
            int[] afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            boolean continuousFocus = afModes != null && Arrays.stream(afModes)
                    .anyMatch(m -> m == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            fitPreview();

            if (!openLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                fail("Camera is busy");
                return;
            }
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice device) {
                    camera = device;
                    openLock.release();
                    startSession(continuousFocus);
                }

                @Override
                public void onDisconnected(CameraDevice device) {
                    openLock.release();
                    device.close();
                    camera = null;
                }

                @Override
                public void onError(CameraDevice device, int error) {
                    openLock.release();
                    device.close();
                    camera = null;
                    fail("Camera error " + error);
                }
            }, handler);
        } catch (CameraAccessException | SecurityException e) {
            openLock.release();
            fail("Can't open the camera: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void startSession(boolean continuousFocus) {
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null || frames == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            request.addTarget(frames.getSurface());
            if (continuousFocus) {
                request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
            List<OutputConfiguration> outputs = List.of(
                    new OutputConfiguration(surface), new OutputConfiguration(frames.getSurface()));
            camera.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs,
                    handler::post, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            repeat();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession s) {
                            fail("Camera setup failed");
                        }
                    }));
        } catch (CameraAccessException | IllegalStateException e) {
            fail("Camera setup failed: " + e.getMessage());
        }
    }

    private void repeat() {
        if (session == null || request == null) return;
        request.set(CaptureRequest.FLASH_MODE, torchOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
        try {
            session.setRepeatingRequest(request.build(), null, handler);
        } catch (CameraAccessException | IllegalStateException e) {
            // The session closed underneath us (paused); the next start rebuilds it.
        }
    }

    private void stop() {
        try {
            boolean locked = openLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            try {
                if (session != null) session.close();
                if (camera != null) camera.close();
                if (frames != null) frames.close();
            } finally {
                session = null;
                camera = null;
                frames = null;
                if (locked) openLock.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
            handler = null;
        }
    }

    /** Center-crops the preview instead of stretching it. The activity is portrait-only. */
    private void fitPreview() {
        float width = preview.getWidth();
        float height = preview.getHeight();
        if (previewSize == null || width == 0 || height == 0) return;
        // The sensor is landscape; the preview shows it rotated upright.
        float content = (float) previewSize.getHeight() / previewSize.getWidth();
        float sx = 1;
        float sy = 1;
        if (width / height > content) sy = (width / content) / height;
        else sx = (height * content) / width;
        Matrix m = new Matrix();
        m.setScale(sx, sy, width / 2, height / 2);
        preview.setTransform(m);
    }

    private static String backCamera(CameraManager manager) throws CameraAccessException {
        String[] ids = manager.getCameraIdList();
        for (String id : ids) {
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        return ids.length > 0 ? ids[0] : null;
    }

    /** The largest size up to 720p; enough for barcodes and cheap to decode. */
    private static Size pickSize(Size[] sizes) {
        Size best = null;
        for (Size s : sizes) {
            if (s.getWidth() > 1280 || s.getHeight() > 720) continue;
            if (best == null || s.getWidth() * s.getHeight() > best.getWidth() * best.getHeight()) best = s;
        }
        return best != null ? best : sizes[sizes.length - 1];
    }

    // Decoding

    private void onFrame(ImageReader source) {
        byte[] luminance;
        int width;
        int height;
        try (Image image = source.acquireLatestImage()) {
            if (image == null || found) return;
            width = image.getWidth();
            height = image.getHeight();
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int stride = plane.getRowStride();
            luminance = new byte[width * height];
            for (int row = 0; row < height; row++) {
                buffer.position(row * stride);
                buffer.get(luminance, row * width, width);
            }
        } catch (IllegalStateException e) {
            return; // Reader closed while pausing.
        }
        // 1D codes are read along rows; try the sensor's landscape orientation, then portrait.
        String code = decode(luminance, width, height);
        if (code == null) code = decode(rotate(luminance, width, height), height, width);
        if (code != null && !found) {
            found = true;
            String result = code;
            runOnUiThread(() -> onBarcode(result));
        }
    }

    private String decode(byte[] data, int width, int height) {
        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
            return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source))).getText();
        } catch (NotFoundException e) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private static byte[] rotate(byte[] data, int width, int height) {
        byte[] out = new byte[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) out[x * height + (height - 1 - y)] = data[y * width + x];
        }
        return out;
    }

    // Result

    private void onBarcode(String code) {
        stop();
        Food existing = Db.get(this).foodByBarcode(code);
        if (existing != null) {
            Toast.makeText(this, "Already in your foods", Toast.LENGTH_SHORT).show();
            if (getCallingActivity() != null) {
                setResult(RESULT_OK, new Intent().putExtra(FoodEditActivity.EXTRA_ID, existing.id));
                finish();
            } else {
                startActivityForResult(new Intent(this, FoodEditActivity.class)
                        .putExtra(FoodEditActivity.EXTRA_ID, existing.id), REQUEST_EDIT);
            }
            return;
        }
        status.setText("Looking up " + code + "…");
        lookups.execute(() -> {
            Food food = null;
            String error = null;
            try {
                food = OpenFoodFacts.lookup(code);
            } catch (IOException | JSONException e) {
                error = e.getMessage();
            }
            Food result = food;
            String message = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Food f = result;
                if (f == null) {
                    f = new Food();
                    f.barcode = code;
                    Toast.makeText(this, message != null ? "Lookup failed: " + message
                            : "Not on Open Food Facts. Enter it from the label.", Toast.LENGTH_LONG).show();
                }
                Intent edit = new Intent(this, FoodEditActivity.class);
                f.toIntent(edit);
                startActivityForResult(edit, REQUEST_EDIT);
            });
        });
    }

    private void fail(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finish();
        });
    }
}
