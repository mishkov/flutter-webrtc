import 'media_stream_track_impl.dart';

mixin FrameStream on MediaStreamTrackNative {
  Future<void> startFrameStream(Function(dynamic frame) onFrame) async {
    throw UnimplementedError();
  }

  Future<void> stopFrameStream() async {
    throw UnimplementedError();
  }
}
