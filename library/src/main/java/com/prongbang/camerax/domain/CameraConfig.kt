package com.prongbang.camerax.domain

import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.impl.ImageOutputConfig.RotationValue

data class CameraConfig(
		@RotationValue
		val rotation: Int = Surface.ROTATION_0,
		@AspectRatio.Ratio
		val aspectRatio: Int = AspectRatio.RATIO_16_9
)