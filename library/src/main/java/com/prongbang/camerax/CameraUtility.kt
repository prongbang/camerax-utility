package com.prongbang.camerax

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import com.prongbang.camerax.domain.CameraConfig
import com.prongbang.camerax.domain.CameraState
import com.prongbang.camerax.domain.ImageResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface CameraUtility {
	fun initial(view: View?, viewFinder: PreviewView?)
	fun setupCamera(): LiveData<CameraState>
	fun bindCamera(config: CameraConfig): LiveData<CameraState>
	fun takePhoto(): LiveData<CameraState>
	fun switchCamera()
}

class CameraXUtility(
		private val context: Context?,
		private val lifecycleOwner: LifecycleOwner,
		private val fileDirectory: FileDirectory,
		private val executorService: ExecutorService
) : CameraUtility, LifecycleObserver {

	private var view: View? = null
	private var viewFinder: PreviewView? = null
	private var preview: Preview? = null
	private var cameraDisplayId: Int = -1
	private var camera: Camera? = null
	private var imageAnalyzer: ImageAnalysis? = null
	private var imageCapture: ImageCapture? = null
	private var cameraProvider: ProcessCameraProvider? = null
	private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
	private var cameraConfig: CameraConfig = CameraConfig()

	private val displayManager by lazy {
		context?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
	}

	/**
	 * We need a display listener for orientation changes that do not trigger a configuration
	 * change, for example if we choose to override config change in manifest or for 180-degree
	 * orientation changes.
	 */
	private val displayListener = object : DisplayManager.DisplayListener {
		override fun onDisplayAdded(displayId: Int) = Unit
		override fun onDisplayRemoved(displayId: Int) = Unit
		override fun onDisplayChanged(displayId: Int) = view?.let { view ->
			if (displayId == cameraDisplayId) {
				imageCapture?.targetRotation = view.display?.rotation ?: 0
				imageAnalyzer?.targetRotation = view.display?.rotation ?: 0
			}
		} ?: Unit
	}

	override fun initial(view: View?, viewFinder: PreviewView?) {
		this.view = view
		this.viewFinder = viewFinder

		// Every time the orientation of device changes, update rotation for use cases
		displayManager?.registerDisplayListener(displayListener, null)

		// Wait for the views to be properly laid out
		viewFinder?.post {
			// Keep track of the display in which this view is attached
			cameraDisplayId = viewFinder.display.displayId
		}
	}

	override fun bindCamera(config: CameraConfig): LiveData<CameraState> {
		val data = MutableLiveData<CameraState>()

		if (context == null) {
			data.postValue(CameraState.Error(Exception("Context is null.")))
			return data
		}

		cameraConfig = config

		val rotation = viewFinder?.display?.rotation ?: 0

		val cameraProvider = cameraProvider
				?: throw IllegalStateException("Camera initialization failed.")

		val cameraSelector = CameraSelector.Builder()
				.requireLensFacing(lensFacing)
				.build()

		preview = Preview.Builder()
				// We request aspect ratio but no resolution
				.setTargetAspectRatio(cameraConfig.aspectRatio)
				// Set initial target rotation
				.setTargetRotation(cameraConfig.rotation)
				.build()

		imageCapture = ImageCapture.Builder()
				.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
				// We request aspect ratio but no resolution to match preview config, but letting
				// CameraX optimize for whatever specific resolution best fits our use cases
				.setTargetAspectRatio(cameraConfig.aspectRatio)
				// Set initial target rotation, we will have to call this again if rotation changes
				// during the lifecycle of this use case
				.setTargetRotation(cameraConfig.rotation)
				.build()

		// ImageAnalysis
		imageAnalyzer = ImageAnalysis.Builder()
				// We request aspect ratio but no resolution
				.setTargetAspectRatio(cameraConfig.aspectRatio)
				// Set initial target rotation, we will have to call this again if rotation changes
				// during the lifecycle of this use case
				.setTargetRotation(cameraConfig.rotation)
				.build()
				// The analyzer can then be assigned to the instance
				.also {
					it.setAnalyzer(executorService, LuminosityAnalyzer { luma ->
						// Values returned from our analyzer are passed to the attached listener
						// We log image analysis results here - you should do something useful
						// instead!
					})
				}

		// Must unbind the use-cases before rebinding them
		cameraProvider.unbindAll()

		try {
			// A variable number of use-cases can be passed here -
			// camera provides access to CameraControl & CameraInfo
			camera = cameraProvider.bindToLifecycle(
					lifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer
			)

			// Attach the viewfinder's surface provider to preview use case
			preview?.setSurfaceProvider(viewFinder?.surfaceProvider)

			data.postValue(CameraState.Previewed)
		} catch (exc: Exception) {
			Log.e(TAG, "Use case binding failed: $exc")
			data.postValue(CameraState.Error(exc))
		}

		return data
	}

	override fun setupCamera(): LiveData<CameraState> {
		val data = MutableLiveData<CameraState>()

		if (context == null) {
			data.postValue(CameraState.Error(java.lang.Exception("Context is null.")))
			return data
		}

		val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
		cameraProviderFuture.addListener(Runnable {

			// CameraProvider
			cameraProvider = cameraProviderFuture.get()

			// Select lensFacing depending on the available cameras
			lensFacing = when {
				hasBackCamera() -> CameraSelector.LENS_FACING_BACK
				hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
				else -> throw IllegalStateException("Back and front camera are unavailable")
			}

			// Build and bind the camera use cases
			data.postValue(CameraState.Configured)

			// Bind camera
			bindCamera(CameraConfig())

		}, ContextCompat.getMainExecutor(context))
		return data
	}

	override fun takePhoto(): LiveData<CameraState> {
		val data = MutableLiveData<CameraState>()

		// Get a stable reference of the modifiable image capture use case
		val imageCapture = imageCapture ?: return data

		// Create time-stamped output file to hold the image
		val photoFile = fileDirectory.photoFile()

		// Setup image capture metadata
		val metadata = ImageCapture.Metadata()
				.apply {
					// Mirror image when using the front camera
					isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
				}

		// Create output options object which contains file + metadata
		val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
				.setMetadata(metadata)
				.build()

		// Set up image capture listener, which is triggered after photo has been taken
		imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
				object : ImageCapture.OnImageSavedCallback {
					override fun onError(exc: ImageCaptureException) {
						Log.i(TAG, "Photo capture failed: ${exc.message}")
						data.postValue(CameraState.Error(exc))
					}

					override fun onImageSaved(output: ImageCapture.OutputFileResults) {
						val savedUri = Uri.fromFile(photoFile)
						Log.i(TAG, "Image path: $savedUri")
						data.postValue(CameraState.Saved(
								ImageResult(file = photoFile, uri = savedUri)))
					}
				})

		return data
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	private fun shutdown() {
		executorService.shutdown()
		displayManager?.unregisterDisplayListener(displayListener)
	}

	override fun switchCamera() {
		lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
			CameraSelector.LENS_FACING_BACK
		} else {
			CameraSelector.LENS_FACING_FRONT
		}
		// Re-bind use cases to update selected camera
		bindCamera(cameraConfig)
	}

	/** Returns true if the device has an available back camera. False otherwise */
	private fun hasBackCamera(): Boolean {
		return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
	}

	/** Returns true if the device has an available front camera. False otherwise */
	private fun hasFrontCamera(): Boolean {
		return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
	}

	companion object {
		private val TAG = CameraXUtility::class.java.simpleName

		fun newInstance(activity: Activity?, lifecycleOwner: LifecycleOwner): CameraXUtility =
				CameraXUtility(
						context = activity,
						lifecycleOwner = lifecycleOwner,
						fileDirectory = OutputFileDirectory(activity),
						executorService = Executors.newSingleThreadExecutor()
				)
	}
}