package com.example.vw_face_recognition_plugin.face_recognition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A custom Android platform view that implements real-time face recognition functionality.
 * 
 * This view provides:
 * - Live camera preview using CameraX
 * - Real-time face detection and recognition against a reference image
 * - Optional reference image preview overlay
 * - Optional similarity score display
 * - Configurable rotation of reference image
 * - Configurable similarity threshold for match/no-match indication
 *
 * @property context The Android context used for creating views and accessing system services
 * @property refImage The reference bitmap image to match faces against
 * @property methodChannel Flutter method channel for sending similarity scores back to Dart
 * @property showRefImagePreview Whether to show the reference image overlay (default true)
 * @property showSimilarityScore Whether to show the similarity score display (default true)
 * @property threshold Similarity threshold for indicating match/no-match (default 0.5)
 * @property degreeRotateRefImage Degrees to rotate reference image (default 90)
 */
class FaceRecognitionPlatformView(
    private val context: Context,
    private val refImage: Bitmap,
    private val methodChannel: MethodChannel,
    private val showRefImagePreview: Boolean = true,
    private val showSimilarityScore: Boolean = true,
    private val threshold: Float = 0.5f,
    private val degreeRotateRefImage: Float = 90f
) : PlatformView {

    private val container: FrameLayout = FrameLayout(context)
    private val previewView: PreviewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    private val overlayImageView: ImageView = ImageView(context)
    private val similarityTextView: TextView = TextView(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val correctedRefImage = refImage.rotate(degreeRotateRefImage)

    init {
        setupUI()
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    /**
     * Sets up the UI components of the face recognition view.
     * 
     * This includes:
     * - Camera preview surface
     * - Reference image overlay with rounded corners (if enabled)
     * - Similarity score display with semi-transparent background (if enabled)
     */
    private fun setupUI() {
        // Camera preview
        container.addView(previewView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Reference image preview with rounded corners
        if (showRefImagePreview) {
            overlayImageView.setImageDrawable(BitmapDrawable(context.resources, correctedRefImage))
            overlayImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            overlayImageView.clipToOutline = true
            overlayImageView.background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.TRANSPARENT)
            }

            val overlayParams = FrameLayout.LayoutParams(200, 200).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = 16
                leftMargin = 16
            }
            container.addView(overlayImageView, overlayParams)
        }

        // Similarity score text view with rounded corners
        if (showSimilarityScore) {
            similarityTextView.textSize = 16f
            similarityTextView.setTextColor(Color.RED)
            similarityTextView.setPadding(16, 8, 16, 8)

            similarityTextView.background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.argb(150, 0, 0, 0)) // semi-transparent black
            }

            val scoreParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 16
                rightMargin = 16
            }

            similarityTextView.text = "Initialising"
            similarityTextView.setTextColor(Color.GREEN)

            container.addView(similarityTextView, scoreParams)
        }
    }

    /**
     * Checks if the app has been granted camera permission.
     *
     * @return true if camera permission is granted, false otherwise
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests camera permission from the user if not already granted.
     * 
     * Uses the activity context to show the system permission dialog.
     */
    private fun requestCameraPermission() {
        val activity = context.findActivity()
        if (activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION
            )
        } else {
            Log.e("FaceRecognition", "Unable to request permission: Activity not found.")
        }
    }

    /**
     * Initializes and starts the camera preview and face recognition analysis.
     * 
     * Sets up:
     * - CameraX preview use case
     * - Image analysis use case with face recognition
     * - Binds use cases to camera lifecycle
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analyzer = FaceRecognitionImageAnalyzer(
                    context = context,
                    refImage = correctedRefImage,
                    onSimilarityComputed = { similarity ->
                        Log.d("FaceRecognition", "Similarity: $similarity")
                        methodChannel.invokeMethod("onSimilarity", similarity)

                        if (showSimilarityScore) {
                            updateSimilarityUI(similarity)
                        }
                    }
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, analyzer)
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                val lifecycleOwner = context.findActivity() as? LifecycleOwner
                if (lifecycleOwner != null) {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    overlayImageView.bringToFront()
                    similarityTextView.bringToFront()
                } else {
                    Log.e("FaceRecognition", "Context is not a LifecycleOwner.")
                }

            } catch (e: Exception) {
                Log.e("FaceRecognition", "Failed to bind camera use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Updates the similarity score UI based on the computed similarity value.
     * 
     * Shows:
     * - "Face not found" in red if similarity is negative
     * - Percentage match in green if above threshold
     * - Percentage match in red if below threshold
     *
     * @param similarity The computed similarity score (-1.0 to 1.0)
     */
    private fun updateSimilarityUI(similarity: Float) {
        similarityTextView.post {
            if (similarity < 0f) {
                similarityTextView.text = "Face not found"
                similarityTextView.setTextColor(Color.RED)
            } else {
                similarityTextView.text = String.format("Matched %.1f%%", similarity * 100)
                similarityTextView.setTextColor(
                    if (similarity >= threshold) Color.GREEN else Color.RED
                )
            }
        }
    }

    override fun getView(): View = container

    override fun dispose() {
        analysisExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_CAMERA_PERMISSION = 1001
    }
}

/**
 * Extension function to find the Activity instance from a Context.
 *
 * Traverses the context chain until an Activity is found or returns null.
 *
 * @return Activity instance if found, null otherwise
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Extension function to rotate a Bitmap by specified degrees.
 *
 * @param degrees The rotation angle in degrees
 * @return A new Bitmap rotated by the specified angle
 */
fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}