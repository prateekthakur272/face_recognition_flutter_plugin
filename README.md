# Face Recognition Flutter Plugin

A cross-platform (Android & iOS) Flutter plugin to perform real-time face recognition using a reference image. It provides a native camera preview, overlays the reference image (optional), and sends back similarity scores between live camera frames and the reference face.


## ✨ Features

* 📷 Platform camera preview using native Android/iOS APIs
* 🧠 Native real-time face recognition using a reference image
* ✨ Configurable similarity threshold and rotation handling
* 📈 Live updates of similarity scores through method channel callbacks
* 🖼️ Option to overlay the reference image and similarity score box




## 🛠️ Installation

Add this to your `pubspec.yaml`:

```yaml
dependencies:
  face_recognition_flutter_plugin:
    git:
      url: https://github.com/prateekthakur272/face_recognition_flutter_plugin.git
```

> Replace the Git URL with your actual repository.




## 🚀 Usage

```dart
import 'package:vw_face_recognition_plugin/face_recognition_flutter_plugin.dart';

FaceRecognitionView(
  refImageBytes: referenceImageBytes, // Uint8List
  width: 300,
  height: 400,
  rotateDegrees: 90,
  threshold: 0.5,
  showRefPreview: true,
  showSimilarityScore: true,
  onSimilarity: (score) {
    print("Similarity score: $score");
  },
)
```




## 📄 Parameters

| Parameter             | Type        | Description                                           |
| --------------------- | ----------- | ----------------------------------------------------- |
| `refImageBytes`       | `Uint8List` | Required. The face image to match against.            |
| `width`               | `double?`   | Width of the camera preview.                          |
| `height`              | `double?`   | Height of the camera preview.                         |
| `onSimilarity`        | `Function`  | Callback with similarity score (0.0 to 1.0).          |
| `showRefPreview`      | `bool`      | Show the reference image overlay.                     |
| `showSimilarityScore` | `bool`      | Show similarity score UI on top right.                |
| `rotateDegrees`       | `double`    | Degrees to rotate reference image (default: 90).      |
| `threshold`           | `double`    | Score threshold for green/red display (default: 0.5). |




## 👍 Best Practices

* Ensure the reference image is a clear front-facing image.
* Use in portrait mode for consistent alignment.
* Adjust `rotateDegrees` depending on how your camera is mounted.




## 🎨 Screenshots

<img src="https://github.com/user-attachments/assets/72a290ea-4f71-4bbc-8f25-5bbcf4e321f4" 
       style="height: 500px; object-fit: cover;" 
       alt="Screenshot">


## 🚧 Platform Support

| Platform | Supported |
| -------- | --------- |
| Android  | ✅         |
| iOS      | ✅         |
| Web      | ❌         |




## ⚙️ MethodChannel

The plugin uses `MethodChannel` to return real-time similarity updates to Dart via:

```dart
onSimilarity: (double score) {
  // Use score to update your UI or logic
}
```


## ✉️ License

MIT License - see [LICENSE](LICENSE) file for details.

**Built with ❤️ by Prateek Thakur**
