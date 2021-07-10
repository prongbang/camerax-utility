package com.prongbang.camerax.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.prongbang.camerax.databinding.CameraPreviewViewBinding

class CameraPreviewView @kotlin.jvm.JvmOverloads constructor(
		context: Context,
		attrs: AttributeSet? = null,
		defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

	private var binding: CameraPreviewViewBinding = CameraPreviewViewBinding.inflate(
			LayoutInflater.from(context))
			.also { addView(it.root) }

	val previewView get() = binding.previewView
	val rotation get() = binding.previewView.display.rotation
	val surfaceProvider get() = binding.previewView.surfaceProvider
}