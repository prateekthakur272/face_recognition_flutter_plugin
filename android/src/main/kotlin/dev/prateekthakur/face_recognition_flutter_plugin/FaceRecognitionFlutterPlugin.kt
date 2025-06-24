package dev.prateekthakur.face_recognition_flutter_plugin

import com.example.vw_face_recognition_plugin.face_recognition.FaceRecognitionViewFactory
import io.flutter.embedding.engine.plugins.FlutterPlugin

/**
 * Main plugin class for the VW Face Recognition Flutter plugin.
 *
 * This plugin provides real-time face recognition capabilities by registering a native Android
 * view factory that handles camera preview and face detection/matching functionality.
 *
 * The plugin implements [FlutterPlugin] to properly handle lifecycle events when the Flutter
 * engine attaches and detaches from the Android side.
 */
class FaceRecognitionFlutterPlugin : FlutterPlugin {
  /**
   * Application context needed for camera and other system services.
   * Initialized when plugin attaches to engine.
   */
  private lateinit var applicationContext: android.content.Context

  /**
   * Called when Flutter engine attaches to this Android plugin.
   *
   * Registers the face recognition view factory with the platform registry to enable
   * creation of native views from Flutter code.
   *
   * @param binding Provides access to Flutter engine resources like binary messenger
   */
  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    applicationContext = binding.applicationContext
    binding.platformViewRegistry.registerViewFactory(
      "face_recognition_view",
      FaceRecognitionViewFactory(binding.binaryMessenger, applicationContext)
    )
  }

  /**
   * Called when Flutter engine detaches from this Android plugin.
   *
   * Currently no cleanup is needed but this could be used to release resources
   * if they are added in the future.
   *
   * @param binding The plugin binding being detached
   */
  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    // cleanup if needed
  }
}