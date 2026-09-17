package com.catguard.detection

import android.content.Context

/**
 * Single place where the concrete detector is chosen.
 *
 * To move CatGuard onto a custom model, add an implementation of [CatDetector]
 * and return it here; no other file needs to change.
 */
object DetectorFactory {

    fun create(
        context: Context,
        modelAsset: String = DetectorDefaults.DEFAULT_MODEL_ASSET,
    ): CatDetector = TfLiteCatDetector.create(context, modelAsset)
}
