import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A widget that provides real-time face recognition and similarity comparison functionality.
///
/// This widget creates a platform view that displays a camera preview and performs face recognition
/// using native platform implementations (Android and iOS). It compares faces detected in the camera
/// feed with a provided reference image in real-time.
///
/// Example usage:
/// ```dart
/// FaceRecognitionView(
///   refImageBytes: imageBytes,
///   onSimilarity: (score) {
///     print('Face similarity score: $score');
///   },
/// )
/// ```
class FaceRecognitionView extends StatefulWidget {
  /// The width of the view. If null, the view will expand to fill available width.
  final double? width;

  /// The height of the view. If null, the view will expand to fill available height.
  final double? height;

  /// Reference face image as byte data (e.g. from `ImagePicker`, `Asset`, `MemoryImage`, etc.)
  /// This image will be used as the reference for face similarity comparison.
  final Uint8List refImageBytes;

  /// Callback function that is triggered whenever a new similarity score is computed.
  ///
  /// The score is a double value between 0.0 and 1.0, where:
  /// * 1.0 indicates a perfect match
  /// * 0.0 indicates no similarity
  final ValueChanged<double>? onSimilarity;

  /// Controls whether to display the reference image preview in the view.
  ///
  /// When true, the reference image will be shown in a small preview window.
  /// Defaults to true.
  final bool showRefPreview;

  /// Controls whether to show the similarity score display in the top-right corner.
  ///
  /// When true, displays a box showing the current similarity score.
  /// Defaults to true.
  final bool showSimilarityScore;

  /// The rotation angle in degrees to apply to the reference image.
  ///
  /// This is useful when the reference image orientation needs to be adjusted.
  /// Defaults to 90.0 degrees to handle portrait camera orientation.
  final double rotateDegrees;

  /// The similarity threshold used for score visualization.
  ///
  /// When the similarity score is above this threshold, the score display will typically
  /// show in green, otherwise in red. This helps visually indicate when a good match is found.
  /// Defaults to 0.5.
  final double threshold;

  /// Creates a face recognition view with the specified parameters.
  ///
  /// The [refImageBytes] parameter is required and must not be null.
  const FaceRecognitionView({
    super.key,
    this.width,
    this.height,
    required this.refImageBytes,
    this.onSimilarity,
    this.showRefPreview = true,
    this.showSimilarityScore = true,
    this.rotateDegrees = 90.0,
    this.threshold = 0.5,
  });

  @override
  State<FaceRecognitionView> createState() => _FaceRecognitionViewState();
}

/// The state class for [FaceRecognitionView].
///
/// Handles the platform view creation and communication with native code.
class _FaceRecognitionViewState extends State<FaceRecognitionView> {
  /// The reference image encoded as base64 string for platform communication
  late final String base64Image;

  /// Channel for communicating with the native platform code
  MethodChannel? _methodChannel;

  @override
  void initState() {
    super.initState();
    base64Image = base64Encode(widget.refImageBytes);
  }

  /// Handles the platform view creation and sets up the method channel for communication
  void _onPlatformViewCreated(int id) {
    _methodChannel = MethodChannel('face_recognition_view_$id');
    _methodChannel!.setMethodCallHandler((call) async {
      if (call.method == 'onSimilarity') {
        final similarity = (call.arguments as num).toDouble();
        widget.onSimilarity?.call(similarity);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final creationParams = {
      'refImage': base64Image,
      'showRefPreview': widget.showRefPreview,
      'rotateDegrees': widget.rotateDegrees,
      'showSimilarityScore': widget.showSimilarityScore,
      'threshold': widget.threshold,
    };

    if (defaultTargetPlatform == TargetPlatform.android) {
      return SizedBox(
        width: widget.width,
        height: widget.height,
        child: AndroidView(
          viewType: 'face_recognition_view',
          layoutDirection: TextDirection.ltr,
          creationParams: creationParams,
          creationParamsCodec: const StandardMessageCodec(),
          onPlatformViewCreated: _onPlatformViewCreated,
        ),
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      return SizedBox(
        width: widget.width,
        height: widget.height,
        child: UiKitView(
          viewType: 'face_recognition_view',
          layoutDirection: TextDirection.ltr,
          creationParams: creationParams,
          creationParamsCodec: const StandardMessageCodec(),
          onPlatformViewCreated: _onPlatformViewCreated,
        ),
      );
    } else {
      return const Text('Face recognition is not supported on this platform.');
    }
  }
}
