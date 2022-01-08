import 'dart:async';

import 'package:flutter/services.dart';

import '../../flutter_webrtc.dart';
import 'media_stream_track_impl.dart';

mixin FrameStream on MediaStreamTrackNative {
  StreamSubscription<dynamic>? _frameStreamSubscription;

  Future<void> startFrameStream(Function(dynamic frame) onFrame) async {
    await WebRTC.invokeMethod(
      'startFrameStream',
      <String, dynamic>{'trackId': id},
    );

    final cameraEventChannel =
        EventChannel('FlutterWebRTC.Method/frameStream/$id');

    // TODO: Implement conversion to readble format
    _frameStreamSubscription =
        cameraEventChannel.receiveBroadcastStream().listen(onFrame);
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
