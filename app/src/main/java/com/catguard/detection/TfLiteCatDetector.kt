package com.catguard.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * [CatDetector] backed by a TFLite object-detection model with embedded metadata
 * (EfficientDet-Lite by default).
 *
 * The model instance is created once and reused for the whole life of the guard
 * session; [detect] performs no model construction and allocates only the
 * TensorImage wrapper around the caller's bitmap.
 */
class TfLiteCatDetector private constructor(
    private val detector: ObjectDetector,
    override val displayName: String,
    private val numThreads: Int,
) : CatDetector {

    override val supportedLabels: List<String> = COCO_LABELS_OF_INTEREST

    private var closed = false

    override fun detect(bitmap: Bitmap, timestampMs: Long): DetectionFrame {
        if (closed) return DetectionFrame.empty(timestampMs)
        val started = SystemClock.elapsedRealtime()
        val results = try {
            detector.detect(TensorImage.fromBitmap(bitmap))
        } catch (t: Throwable) {
            Log.e(TAG, "Inference failed", t)
            return DetectionFrame.empty(timestampMs)
        }
        val elapsed = SystemClock.elapsedRealtime() - started

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val detections = ArrayList<Detection>(results.size)
        for (result in results) {
            val category = result.categories.maxByOrNull { it.score } ?: continue
            val b = result.boundingBox
            detections += Detection(
                label = category.label,
                score = category.score,
                box = NormalizedBox(b.left / w, b.top / h, b.right / w, b.bottom / h).clampToUnit(),
            )
        }
        return DetectionFrame(
            timestampMs = timestampMs,
            detections = detections,
            inferenceMs = elapsed,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { detector.close() }
            .onFailure { Log.w(TAG, "Error closing detector", it) }
    }

    override fun toString() = "$displayName (${numThreads}t)"

    companion object {
        private const val TAG = "TfLiteCatDetector"

        /** Not the full COCO list - just what matters for reasoning about false alarms. */
        private val COCO_LABELS_OF_INTEREST =
            listOf("cat", "dog", "person", "bird", "horse", "sheep", "cow", "bear")

        /**
         * Loads a detector from an APK asset.
         *
         * @throws DetectorUnavailableException with a message suitable for display
         */
        fun create(
            context: Context,
            modelAsset: String = DetectorDefaults.DEFAULT_MODEL_ASSET,
            numThreads: Int = recommendedThreadCount(),
        ): TfLiteCatDetector {
            assertAssetPresent(context, modelAsset)
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setNumThreads(numThreads).build())
                .setScoreThreshold(DetectorDefaults.MODEL_SCORE_FLOOR)
                .setMaxResults(DetectorDefaults.MAX_RESULTS)
                .build()
            val detector = try {
                ObjectDetector.createFromFileAndOptions(context, modelAsset, options)
            } catch (t: Throwable) {
                throw DetectorUnavailableException(
                    "Could not load the detection model '$modelAsset'. " +
                        "The file may be corrupt, or missing its TFLite metadata.",
                    t,
                )
            }
            return TfLiteCatDetector(detector, modelAsset.removeSuffix(".tflite"), numThreads)
        }

        private fun assertAssetPresent(context: Context, modelAsset: String) {
            val present = runCatching {
                context.assets.open(modelAsset).use { it.read() }
                true
            }.getOrDefault(false)
            if (!present) {
                throw DetectorUnavailableException(
                    "Detection model '$modelAsset' is not bundled in the APK. " +
                        "Place it in app/src/main/assets/ and rebuild.",
                )
            }
        }

        /**
         * Two threads on most phones: enough to keep inference under ~150 ms on an
         * old device without pinning every core (which cooks the phone on a 24/7 run).
         */
        fun recommendedThreadCount(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(1, 4).let { cores ->
                if (cores >= 4) 2 else 1
            }
    }
}
