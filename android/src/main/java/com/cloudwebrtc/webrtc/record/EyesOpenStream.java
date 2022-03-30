package com.cloudwebrtc.webrtc.record;

import static com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL;

import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;
import org.webrtc.YuvHelper;

import java.nio.ByteBuffer;
import java.util.List;

import io.flutter.plugin.common.EventChannel;

public class EyesOpenStream implements VideoSink, EventChannel.StreamHandler {
    private final VideoTrack videoTrack;
    private EventChannel.EventSink sink;
    private boolean inProgress = false;

    private final FaceDetectorOptions realTimeOpts =
            new FaceDetectorOptions.Builder()
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(LANDMARK_MODE_ALL)
                    .build();
    private final FaceDetector detector = FaceDetection.getClient(realTimeOpts);
    private final OnFacesListener onFacesListener = new OnFacesListener();
    private final OnErrorListener onErrorListener = new OnErrorListener();

    private static final String TAG = "EyesOpenStream";

    public EyesOpenStream(VideoTrack track) {
        videoTrack = track;
        Log.d(TAG, "Enter to constructor");
    }

    @Override
    public void onFrame(VideoFrame videoFrame) {
        if (inProgress) {
            return;
        }
        inProgress = true;

        YuvImage yuvImage = videoFrameToYuvImage(videoFrame);

        int frameRotation;
        System.out.println("isRunning on emulator = " + isRunningOnEmulator());
        if (isRunningOnEmulator()) {
            frameRotation = decreaseDegreeBy90(videoFrame.getRotation());
        } else {
            frameRotation = videoFrame.getRotation();
        }

        InputImage inputImage = InputImage.fromByteArray(
                yuvImage.getYuvData(),
                yuvImage.getWidth(),
                yuvImage.getHeight(),
                frameRotation,
                yuvImage.getYuvFormat()
        );



        detector.process(inputImage)
                .addOnSuccessListener(onFacesListener)
                .addOnFailureListener(onErrorListener);

    }

    private boolean isRunningOnEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("sdk_gphone64_arm64")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }

    private int decreaseDegreeBy90(int degree) {
        if (!isDegreeCorrect(degree)) throw new ArithmeticException("The degree must be in range 0..360");

        int decreasedDegree = degree - 90;
        if (decreasedDegree < 0) {
            decreasedDegree += 360;
        }
        return  decreasedDegree;
    }

    private boolean isDegreeCorrect(int degree) {
        return (0 <= degree) && (degree <= 360);
    }

    @NonNull
    private YuvImage videoFrameToYuvImage(@NonNull VideoFrame videoFrame) {
        VideoFrame.I420Buffer i420Buffer = videoFrame.getBuffer().toI420();
        ByteBuffer y = i420Buffer.getDataY();
        ByteBuffer u = i420Buffer.getDataU();
        ByteBuffer v = i420Buffer.getDataV();
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();
        int[] strides = new int[] {
                i420Buffer.getStrideY(),
                i420Buffer.getStrideU(),
                i420Buffer.getStrideV()
        };
        i420Buffer.release();
        final int chromaWidth = (width + 1) / 2;
        final int chromaHeight = (height + 1) / 2;
        final int minSize = width * height + chromaWidth * chromaHeight * 2;
        ByteBuffer yuvBuffer = ByteBuffer.allocateDirect(minSize);
        YuvHelper.I420ToNV12(y, strides[0], v, strides[2], u, strides[1], yuvBuffer, width, height);

        return new YuvImage(
                yuvBuffer.array(),
                ImageFormat.NV21,
                width,
                height,
                strides
        );
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        Log.d(TAG, "onListen was called");
        sink = events;
        videoTrack.addSink(this);
    }

    @Override
    public void onCancel(Object arguments) {
        Log.d(TAG, "onCancel was called");
        videoTrack.removeSink(this);
    }

    private void postResult(boolean eyesOpen) {
        sink.success(eyesOpen);
        inProgress = false;

    }

    private void postError(String message, @Nullable Object details) {
        final String DETECT_EXCEPTION_CODE = "DetectException";
        sink.error(DETECT_EXCEPTION_CODE, message, details);
        inProgress = false;
    }

    private void postError(String message) {
        postError(message, null);
    }

    private class OnFacesListener implements OnSuccessListener<List<Face>> {
        @Override
        public void onSuccess(@NonNull List<Face> faces) {
            boolean onlyOneFace = faces.size() == 1;
            if (onlyOneFace) {
                Float leftEyeOpen = faces.get(0).getLeftEyeOpenProbability();
                Float rightEyeOpen = faces.get(0).getRightEyeOpenProbability();
                if ((leftEyeOpen != null) && (rightEyeOpen != null)) {
                    final double THRESHOLD = 0.6;
                    boolean eyesOpen = (leftEyeOpen > THRESHOLD) && (rightEyeOpen > THRESHOLD);
                    postResult(eyesOpen);
                } else {
                    postError("getLeftEyeOpenProbability or getRightEyeOpenProbability returned Null");
                }
            } else {
                postError("Zero faces or more than 1 face on frame", faces.size());
            }
        }
    }

    private class OnErrorListener implements OnFailureListener {
        @Override
        public void onFailure(@NonNull Exception e) {
            postError(e.getLocalizedMessage(), e);
        }
    }
}