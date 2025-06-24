import Flutter
import UIKit

public class FaceRecognitionFlutterPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
      let channel = FlutterMethodChannel(name: "vw_face_recognition_plugin", binaryMessenger: registrar.messenger())
      let instance = VwFaceRecognitionPlugin()
      registrar.addMethodCallDelegate(instance, channel: channel)

      let factory = FaceRecognitionViewFactory(messenger: registrar.messenger())
      registrar.register(factory, withId: "face_recognition_view")
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
      switch call.method {
      case "getPlatformVersion":
        result("iOS " + UIDevice.current.systemVersion)
      default:
        result(FlutterMethodNotImplemented)
      }
    }
}
