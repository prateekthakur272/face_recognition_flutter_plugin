package com.example.vw_face_recognition_plugin.face_recognition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Image analyzer class that performs real-time face recognition by comparing camera frames
 * against a reference face image using FaceNet embeddings.
 *
 * This analyzer uses Google's ML Kit for face detection and a TensorFlow Lite model (FaceNet)
 * for generating face embeddings. It computes similarity scores between the reference face
 * and faces detected in camera frames.
 *
 * @property context Android context used for accessing assets
 * @property refImage Reference bitmap image containing the face to match against
 * @property onSimilarityComputed Callback function that receives similarity scores (0.0 to 1.0)
 */
class FaceRecognitionImageAnalyzer(
    private val context: Context,
    private val refImage: Bitmap,
    private val onSimilarityComputed: (Float) -> Unit
) : ImageAnalysis.Analyzer {

    /** ML Kit face detector instance */
    private val detector: FaceDetector
    
    /** TensorFlow Lite interpreter for running the FaceNet model */
    private val interpreter: Interpreter
    
    /** Cached embedding of the reference face */
    private var refEmbedding: FloatArray? = null
    
    /** Required input size for the FaceNet model */
    private val inputSize = 160
    
    /** Flag indicating if reference embedding computation is complete */
    private var isRefComputed = false

    init {
        // Initialize face detector with fast performance mode
        detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
        // Load and initialize FaceNet model
        interpreter = Interpreter(loadModelFile("facenet.tflite"))
        computeRefEmbedding()
    }

    /**
     * Analyzes camera frames to detect faces and compute similarity with reference face.
     *
     * For each frame:
     * 1. Detects faces using ML Kit
     * 2. Crops and processes the first detected face
     * 3. Generates face embedding using FaceNet
     * 4. Computes cosine similarity with reference embedding
     * 5. Reports similarity score via callback
     *
     * @param imageProxy Camera frame to analyze
     */
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        if (!isRefComputed) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onSimilarityComputed(-1f)
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                val faceBox = faces[0].boundingBox
                val bitmap = toBitmap(mediaImage)
                val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)

                val safeRect = Rect(
                    faceBox.left.coerceAtLeast(0),
                    faceBox.top.coerceAtLeast(0),
                    faceBox.right.coerceAtMost(rotatedBitmap.width),
                    faceBox.bottom.coerceAtMost(rotatedBitmap.height)
                )

                val croppedFace = try {
                    Bitmap.createBitmap(
                        rotatedBitmap,
                        safeRect.left,
                        safeRect.top,
                        safeRect.width(),
                        safeRect.height()
                    )
                } catch (e: Exception) {
                    Log.e("FaceAnalyzer", "Failed to crop: ${e.message}")
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                val currentEmbedding = getFaceEmbedding(croppedFace)
                val similarity = cosineSimilarity(refEmbedding!!, currentEmbedding)
                onSimilarityComputed(similarity)

                imageProxy.close()
            }
            .addOnFailureListener {
                Log.e("FaceAnalyzer", "Face detection failed: ${it.message}")
                imageProxy.close()
            }
    }

    /**
     * Computes and caches the embedding for the reference face image.
     * 
     * This is called during initialization to prepare for subsequent comparisons.
     * Sets [isRefComputed] flag when complete.
     */
    private fun computeRefEmbedding() {
        val inputImage = InputImage.fromBitmap(refImage, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.e("FaceAnalyzer", "No face in reference image.")
                    return@addOnSuccessListener
                }

                val faceBox = faces[0].boundingBox
                val safeRect = Rect(
                    faceBox.left.coerceAtLeast(0),
                    faceBox.top.coerceAtLeast(0),
                    faceBox.right.coerceAtMost(refImage.width),
                    faceBox.bottom.coerceAtMost(refImage.height)
                )

                val croppedRef = try {
                    Bitmap.createBitmap(
                        refImage,
                        safeRect.left,
                        safeRect.top,
                        safeRect.width(),
                        safeRect.height()
                    )
                } catch (e: Exception) {
                    Log.e("FaceAnalyzer", "Error cropping reference face: ${e.message}")
                    return@addOnSuccessListener
                }

                refEmbedding = getFaceEmbedding(croppedRef)
                isRefComputed = true
                Log.d("FaceAnalyzer", "Reference embedding ready.")
            }
            .addOnFailureListener {
                Log.e("FaceAnalyzer", "Failed to detect face in reference image: ${it.message}")
            }
    }

    /**
     * Generates a 128-dimensional face embedding using the FaceNet model.
     *
     * Process:
     * 1. Resize image to required input size
     * 2. Convert to normalized float values
     * 3. Run through FaceNet model
     * 4. L2 normalize the output embedding
     *
     * @param bitmap Face image to generate embedding for
     * @return Normalized 128-dimensional float array embedding
     */
    private fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF).toFloat()
                val g = (pixel shr 8 and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                inputBuffer.putFloat((r - 127.5f) / 128f)
                inputBuffer.putFloat((g - 127.5f) / 128f)
                inputBuffer.putFloat((b - 127.5f) / 128f)
            }
        }

        val embedding = Array(1) { FloatArray(128) }
        interpreter.run(inputBuffer.rewind(), embedding)
        return l2Normalize(embedding[0])
    }

    /**
     * Computes cosine similarity between two face embeddings.
     *
     * @param a First embedding vector
     * @param b Second embedding vector
     * @return Similarity score between 0.0 and 1.0
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB))
    }

    /**
     * Performs L2 normalization on a vector.
     *
     * @param vector Input vector to normalize
     * @return Normalized vector
     */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.fold(0f) { acc, v -> acc + v * v })
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    /**
     * Rotates a bitmap by the specified degrees.
     *
     * @param bitmap Bitmap to rotate
     * @param rotationDegrees Degrees to rotate by
     * @return Rotated bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Converts a YUV_420_888 image to a Bitmap.
     *
     * @param image YUV formatted image
     * @return RGB bitmap
     */
    private fun toBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val jpegBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /**
     * Loads a TensorFlow Lite model file from assets.
     *
     * @param modelName Name of the model file in assets
     * @return ByteBuffer containing the model
     */
    private fun loadModelFile(modelName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
    }
}