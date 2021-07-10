package com.prongbang.camerax.domain

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class ImageResult(
		val uri: Uri?,
		val file: File?
) : Parcelable