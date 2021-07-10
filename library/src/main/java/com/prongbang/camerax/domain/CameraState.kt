package com.prongbang.camerax.domain

sealed class CameraState {
	object Configured : CameraState()
	object Previewed : CameraState()
	data class Saved(val data: ImageResult) : CameraState()
	data class Error(val exception: Exception) : CameraState()
}
