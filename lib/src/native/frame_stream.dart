import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart';

import '../../flutter_webrtc.dart';
import 'media_stream_track_impl.dart';

mixin FrameStream on MediaStreamTrackNative {
  StreamSubscription<dynamic>? _frameStreamSubscription;

  Future<void> startFrameStream(Function(Uint8List frame) onFrame) async {
    await WebRTC.invokeMethod(
      'startFrameStream',
      <String, dynamic>{'trackId': id},
    );

    final cameraEventChannel =
        EventChannel('FlutterWebRTC.Method/frameStream/$id');

    // TODO: Implement conversion to readble format
    _frameStreamSubscription =
        cameraEventChannel.receiveBroadcastStream().listen((dynamic frameData) {
      if (frameData is List<int>) {
        onFrame(Uint8List.fromList(frameData));
      } else if (frameData is Uint8List) {
        onFrame(frameData);
      }
    });
  }

  Future<void> stopFrameStream() async {
    await WebRTC.invokeMethod(
      'stopFrameStream',
      <String, dynamic>{'trackId': id},
    );

    await _frameStreamSubscription?.cancel();
    _frameStreamSubscription = null;
  }
}
