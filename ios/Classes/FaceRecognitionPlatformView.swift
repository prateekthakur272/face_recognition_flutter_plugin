import Flutter
import UIKit
import AVFoundation

class FaceRecognitionPlatformView: NSObject, FlutterPlatformView {
  private let container: UIView
  private let previewLayer: AVCaptureVideoPreviewLayer
  private let refImageView: UIImageView
  private let scoreLabel: UILabel
  private let captureSession = AVCaptureSession()
  private let channel: FlutterMethodChannel

  private let threshold: Float
  private let showRef: Bool
  private let showScore: Bool
  private let rotateDegrees: CGFloat

  init(
    frame: CGRect,
    viewId: Int64,
    params: [String: Any],
    messenger: FlutterBinaryMessenger
  ) {
    container = UIView(frame: frame)
    refImageView = UIImageView()
    scoreLabel = UILabel()
    threshold = (params["threshold"] as? NSNumber)?.floatValue ?? 0.5
    showRef = (params["showRefPreview"] as? Bool) ?? true
    showScore = (params["showSimilarityScore"] as? Bool) ?? true
    rotateDegrees = CGFloat((params["rotateDegrees"] as? NSNumber)?.floatValue ?? 90)

    channel = FlutterMethodChannel(name: "face_recognition_view_\(viewId)", binaryMessenger: messenger)

    // Decode ref image
    if let base64Str = params["refImage"] as? String,
       let data = Data(base64Encoded: base64Str),
       let image = UIImage(data: data) {
      let rotated = image.rotated(by: rotateDegrees)
      refImageView.image = rotated
    }

    // Setup preview
    let backend = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front)!
    let input = try? AVCaptureDeviceInput(device: backend)
    if let input = input, captureSession.canAddInput(input) {
      captureSession.addInput(input)
    }
    previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
    previewLayer.videoGravity = .resizeAspectFill

    super.init()
    setupUI(frame: frame)
    startSession()
    setupAnalyzer()
  }

  private func setupUI(frame: CGRect) {
    container.frame = frame
    previewLayer.frame = container.bounds
    container.layer.addSublayer(previewLayer)

    if showRef {
      refImageView.frame = CGRect(x: 16, y: 16, width: 200, height: 200)
      refImageView.contentMode = .scaleAspectFill
      refImageView.layer.cornerRadius = 24
      refImageView.clipsToBounds = true
      container.addSubview(refImageView)
    }

    if showScore {
      scoreLabel.frame = CGRect(x: container.bounds.width - 150, y: 16, width: 140, height: 40)
      scoreLabel.backgroundColor = UIColor(white: 0, alpha: 0.6)
      scoreLabel.textColor = UIColor.red
      scoreLabel.font = UIFont.systemFont(ofSize: 14, weight: .medium)
      scoreLabel.textAlignment = .center
      scoreLabel.layer.cornerRadius = 24
      scoreLabel.clipsToBounds = true
      scoreLabel.text = "Initializing"
      container.addSubview(scoreLabel)
    }
  }

  private func startSession() {
    let queue = DispatchQueue(label: "camera_queue")
    captureSession.startRunning()
    previewLayer.connection?.videoOrientation = .portrait
  }

  private func setupAnalyzer() {
    // Use AVFoundation + MLKit/CoreML to analyze frames...
    // For each frame:
    //   detect face — if none: call updateUI(similarity: -1)
    //   otherwise compute embedding, compare to ref image, compute cosine sim
    //   call `channel.invokeMethod("onSimilarity", arguments: sim)`
    // and `updateUI(similarity: sim)`
  }

  private func updateUI(similarity: Float) {
    DispatchQueue.main.async {
      if similarity < 0 {
        self.scoreLabel.text = "Face not found"
        self.scoreLabel.textColor = .red
      } else {
        let pct = Int(similarity * 100)
        self.scoreLabel.text = "Matched \(pct)%"
        self.scoreLabel.textColor = (similarity >= self.threshold ? .green : .red)
      }
    }
  }

  func view() -> UIView {
    return container
  }

  func dispose() {
    captureSession.stopRunning()
  }
}

extension UIImage {
  func rotated(by degrees: CGFloat) -> UIImage {
    let radians = degrees * .pi / 180
    var newSize = CGRect(origin: .zero, size: size)
                 .applying(CGAffineTransform(rotationAngle: radians))
                 .integral.size
    let renderer = UIGraphicsImageRenderer(size: newSize)
    return renderer.image { ctx in
      ctx.cgContext.translateBy(x: newSize.width/2, y: newSize.height/2)
      ctx.cgContext.rotate(by: radians)
      draw(in: CGRect(x: -size.width/2, y: -size.height/2,
                      width: size.width, height: size.height))
    }
  }
}