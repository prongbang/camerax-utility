package com.prongbang.camerax

import android.content.ContextWrapper
import androidx.camera.core.ImageCapture
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

interface FileDirectory {
	fun photoFile(): File
}

class OutputFileDirectory(
		private val context: ContextWrapper?
) : FileDirectory {

	private var imageCapture: ImageCapture? = null

	override fun photoFile(): File {
		val mediaDir = context?.externalMediaDirs?.firstOrNull()
				?.let {
					File(it, DIRECTORY).apply { mkdirs() }
				}
		val outputDirectory = if (mediaDir != null && mediaDir.exists()) {
			mediaDir
		} else {
			context?.filesDir
		}

		return File(outputDirectory, SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(
				System.currentTimeMillis()) + EXT)
	}

	companion object {
		private const val EXT = ".jpg"
		private const val DIRECTORY = "myais"
		private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
	}
}