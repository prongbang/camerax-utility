package com.prongbang.cameraxutility

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.karumi.dexter.Dexter
import com.prongbang.camerax.CameraXUtility
import com.prongbang.camerax.domain.CameraState
import com.prongbang.cameraxutility.databinding.FragmentCameraBinding
import com.prongbang.dexter.DexterPermissionsUtility
import com.prongbang.dexter.MultipleCheckPermissionsListenerImpl
import com.prongbang.dexter.PermissionsChecker
import com.prongbang.dexter.PermissionsCheckerListenerImpl
import com.prongbang.dexter.PermissionsUtility
import com.prongbang.dexter.SingleCheckPermissionListenerImpl

class CameraFragment : Fragment() {

	private val cameraUtility by lazy {
		CameraXUtility.newInstance(activity, this)
	}

	private val permissionsUtility: PermissionsUtility by lazy {
		DexterPermissionsUtility(
				Dexter.withContext(requireContext()),
				SingleCheckPermissionListenerImpl(),
				MultipleCheckPermissionsListenerImpl(),
				PermissionsCheckerListenerImpl(requireContext())
		)
	}

	private lateinit var binding: FragmentCameraBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		lifecycle.addObserver(cameraUtility)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
	                          savedInstanceState: Bundle?): View? {
		binding = FragmentCameraBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		cameraUtility.initial(view, binding.viewFinder.previewView)
		initView()
		initLoad()
	}

	private fun initView() {
		binding.apply {
			captureButton.setOnClickListener { takePhoto() }
		}
	}

	private fun initLoad() {
		requestCameraPermissions()
	}

	private fun requestCameraPermissions() {
		permissionsUtility.isCameraGranted(object : PermissionsChecker {
			override fun onGranted() {
				cameraUtility.setupCamera()
			}

			override fun onNotGranted() {
				Toast.makeText(context, "Please allow camera permission", Toast.LENGTH_SHORT)
						.show()
			}
		})
	}

	private fun takePhoto() {
		cameraUtility.takePhoto()
				.observe(viewLifecycleOwner) {
					when (it) {
						is CameraState.Saved -> {
						}
						else -> {
						}
					}
				}
	}

}