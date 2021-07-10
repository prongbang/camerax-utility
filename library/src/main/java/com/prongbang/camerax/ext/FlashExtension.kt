package com.prongbang.camerax.ext

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup

const val ANIMATION_FAST_MILLIS = 50L
const val ANIMATION_SLOW_MILLIS = 100L

fun ViewGroup.flashAnimation() {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
		postDelayed({
			foreground = ColorDrawable(Color.WHITE)
			postDelayed({ foreground = null },
					ANIMATION_FAST_MILLIS)
		}, ANIMATION_SLOW_MILLIS)
	}
}