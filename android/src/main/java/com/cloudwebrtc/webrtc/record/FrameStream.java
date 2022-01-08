package com.cloudwebrtc.webrtc.record;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.annotation.NonNull;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;
import org.webrtc.YuvHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import io.flutter.plugin.common.EventChannel;

public class FrameStream implements VideoSink, EventChannel.StreamHandler {
    private final VideoTrack videoTrack;
    private EventChannel.EventSink sink;

    public FrameStream(VideoTrack track) {
        videoTrack = track;
    }

    @Override
    public void onFrame(VideoFrame videoFrame) {
        YuvImage yuvImage = videoFrameToYuvImage(videoFrame);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            yuvImage.compressToJpeg(
                    new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()),
                    100,
                    outputStream
            );

            switch (videoFrame.getRotation()) {
                case 0:
                    sink.success(outputStream.toByteArray());
                    break;
                case 90:
                case 180:
                case 270:
                    byte[] bytes = outputStream.toByteArray();
                    Bitmap original = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                    Matrix matrix = new Matrix();
                    matrix.postRotate(videoFrame.getRotation());

                    Bitmap rotated = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);

                    ByteArrayOutputStream rotatedOutputStream = new ByteArrayOutputStream();
                    rotated.compress(Bitmap.CompressFormat.JPEG, 100, rotatedOutputStream);

                    sink.success(rotatedOutputStream.toByteArray());
                    break;
                default:
                    // Rotation is checked to always be 0, 90, 180 or 270 by VideoFrame
                    throw new RuntimeException("Invalid rotation");
            }
        } catch (IOException io) {
            sink.error("IOException", io.getLocalizedMessage(), io);
        } catch (IllegalArgumentException iae) {
            sink.error("IllegalArgumentException", iae.getLocalizedMessage(), iae);
        }
    }

    @NonNull
    private YuvImage videoFrameToYuvImage(@NonNull VideoFrame videoFrame) {
        videoFrame.retain();
        VideoFrame.Buffer buffer = videoFrame.getBuffer();
        VideoFrame.I420Buffer i420Buffer = buffer.toI420();
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
        final int chromaWidth = (width + 1) / 2;
        final int chromaHeight = (height + 1) / 2;
        final int minSize = width * height + chromaWidth * chromaHeight * 2;
        ByteBuffer yuvBuffer = ByteBuffer.allocateDirect(minSize);
        YuvHelper.I420ToNV12(y, strides[0], v, strides[2], u, strides[1], yuvBuffer, width, height);
        YuvImage yuvImage = new YuvImage(
                yuvBuffer.array(),
                ImageFormat.NV21,
                width,
                height,
                strides
        );
        videoFrame.release();

        return yuvImage;
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        sink = events;
        videoTrack.addSink(this);
    }

    @Override
    public void onCancel(Object arguments) {
        videoTrack.removeSink(this);
    }
}