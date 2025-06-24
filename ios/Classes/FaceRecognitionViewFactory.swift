import Flutter
import UIKit

public class FaceRecognitionViewFactory: NSObject, FlutterPlatformViewFactory {
  private let messenger: FlutterBinaryMessenger

  init(messenger: FlutterBinaryMessenger) {
    self.messenger = messenger
    super.init()
  }

  public func create(
    withFrame frame: CGRect,
    viewIdentifier viewId: Int64,
    arguments args: Any?
  ) -> FlutterPlatformView {
    let params = args as? [String: Any] ?? [:]
    return FaceRecognitionPlatformView(
      frame: frame,
      viewId: viewId,
      params: params,
      messenger: messenger
    )
  }
}