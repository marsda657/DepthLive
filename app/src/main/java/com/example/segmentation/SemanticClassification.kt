package com.example.segmentation

import com.example.model.LayerType

internal fun layerForPixel(foregroundClass: Int, landscapeClass: Int): LayerType =
    if (foregroundClass in 1..20) LayerType.FOREGROUND
    else Ade20kLayers.layerFor(landscapeClass) ?: LayerType.BACKGROUND

// Pascal VOC class 16 is potted plant; other detected objects must remain rigid.
internal fun isSwayablePixel(foregroundClass: Int, landscapeClass: Int): Boolean =
    Ade20kLayers.isVegetation(landscapeClass) &&
        (foregroundClass !in 1..20 || foregroundClass == 16)
