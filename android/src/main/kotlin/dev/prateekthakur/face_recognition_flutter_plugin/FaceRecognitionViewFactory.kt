package com.example.vw_face_recognition_plugin.face_recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * Factory class for creating FaceRecognitionPlatformView instances.
 *
 * This factory handles the creation of native Android views that provide face recognition
 * functionality. It processes parameters passed from Flutter and initializes the view with
 * appropriate configuration.
 *
 * @property messenger Flutter binary messenger for setting up method channels
 * @property context Android application context
 */
class FaceRecognitionViewFactory(
    private val messenger: BinaryMessenger,
    private val context: Context
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    /**
     * Creates a new instance of FaceRecognitionPlatformView.
     *
     * This method processes the arguments passed from Flutter and configures the view accordingly.
     * It handles:
     * - Decoding the reference image from base64
     * - Setting up preview and UI options
     * - Configuring the method channel for similarity score callbacks
     *
     * @param context Android context for creating the view
     * @param viewId Unique identifier for the platform view instance
     * @param args Map of arguments passed from Flutter containing view configuration
     * @return A new PlatformView instance configured with the provided parameters
     * @throws IllegalArgumentException if the reference image is invalid or missing
     */
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        // Retrieve params from Dart
        val params = args as? Map<String, Any>

        val base64Image = params?.get("refImage") as? String
        val showPreview = params?.get("showRefPreview") as? Boolean ?: true
        val rotateDegrees = (params?.get("rotateDegrees") as? Number)?.toFloat() ?: 90f
        val showSimilarityScore = params?.get("showSimilarityScore") as? Boolean ?: true
        val threshold = (params?.get("threshold") as? Number)?.toFloat() ?: 0.5f

        val bitmap: Bitmap? = base64Image?.let { decodeBase64ToBitmap(it) }

        if (bitmap == null) {
            throw IllegalArgumentException("Invalid or missing reference image")
        }

        val channel = MethodChannel(messenger, "face_recognition_view_$viewId")

        return FaceRecognitionPlatformView(
            context = context,
            refImage = bitmap,
            methodChannel = channel,
            showRefImagePreview = showPreview,
            showSimilarityScore = showSimilarityScore,
            threshold = threshold,
            degreeRotateRefImage = rotateDegrees
        )
    }

    /**
     * Decodes a base64 string into a Bitmap image.
     *
     * This helper method safely converts the base64-encoded image data received from Flutter
     * into an Android Bitmap that can be used for face recognition.
     *
     * @param base64Str The base64-encoded string representation of the image
     * @return The decoded Bitmap, or null if decoding fails
     */
    private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}