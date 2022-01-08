import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:webrtc_interface/webrtc_interface.dart';

import '../helper.dart';
import 'utils.dart';

class MediaStreamTrackNative extends MediaStreamTrack {
  MediaStreamTrackNative(this._trackId, this._label, this._kind, this._enabled);

  factory MediaStreamTrackNative.fromMap(Map<dynamic, dynamic> map) {
    return MediaStreamTrackNative(
        map['id'], map['label'], map['kind'], map['enabled']);
  }
  final String _trackId;
  final String _label;
  final String _kind;
  bool _enabled;

  bool _muted = false;

  StreamSubscription<dynamic>? _frameStreamSubscription;

  @override
  set enabled(bool enabled) {
    WebRTC.invokeMethod('mediaStreamTrackSetEnable',
        <String, dynamic>{'trackId': _trackId, 'enabled': enabled});
    _enabled = enabled;

    if (kind == 'audio') {
      _muted = !enabled;
      muted ? onMute?.call() : onUnMute?.call();
    }
  }

  @override
  bool get enabled => _enabled;

  @override
  String get label => _label;

  @override
  String get kind => _kind;

  @override
  String get id => _trackId;

  @override
  bool get muted => _muted;

  @override
  Future<bool> hasTorch() => WebRTC.invokeMethod(
        'mediaStreamTrackHasTorch',
        <String, dynamic>{'trackId': _trackId},
      ).then((value) => value ?? false);

  @override
  Future<void> setTorch(bool torch) => WebRTC.invokeMethod(
        'mediaStreamTrackSetTorch',
        <String, dynamic>{'trackId': _trackId, 'torch': torch},
      );

  @override
  Future<bool> switchCamera() => Helper.switchCamera(this);

  @override
  void enableSpeakerphone(bool enable) async {
    print('MediaStreamTrack:enableSpeakerphone $enable');
    await WebRTC.invokeMethod(
      'enableSpeakerphone',
      <String, dynamic>{'trackId': _trackId, 'enable': enable},
    );
  }

  @override
  Future<ByteBuffer> captureFrame() async {
    var filePath = await getTemporaryDirectory();
    await WebRTC.invokeMethod(
      'captureFrame',
      <String, dynamic>{
        'trackId': _trackId,
        'path': filePath.path + '/captureFrame.png'
      },
    );
    return File(filePath.path + '/captureFrame.png')
        .readAsBytes()
        .then((value) => value.buffer);
  }

  Future<void> startFrameStream(Function(Uint8List frame) onFrame) async {
    await WebRTC.invokeMethod(
      'startFrameStream',
      <String, dynamic>{'trackId': id},
    );

    final cameraEventChannel =
        EventChannel('FlutterWebRTC.Method/frameStream/$id');

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

  @override
  Future<void> applyConstraints([Map<String, dynamic>? constraints]) {
    if (constraints == null) return Future.value();

    var _current = getConstraints();
    if (constraints.containsKey('volume') &&
        _current['volume'] != constraints['volume']) {
      Helper.setVolume(constraints['volume'], this);
    }

    return Future.value();
  }

  @override
  Future<void> dispose() async {
    return stop();
  }

  @override
  Future<void> stop() async {
    await WebRTC.invokeMethod(
      'trackDispose',
      <String, dynamic>{'trackId': _trackId},
    );
  }
}
