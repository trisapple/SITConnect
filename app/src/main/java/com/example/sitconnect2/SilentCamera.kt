package com.example.sitconnect2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class SilentCamera(private val context: Context) {
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    interface Callback {
        fun onImageSaved(file: File?)
    }

    @SuppressLint("MissingPermission")
    fun takePicture(callback: Callback) {
        val mainHandler = Handler(Looper.getMainLooper())
        
        var callbackCalled = false
        fun safeCall(f: File?) {
            if (!callbackCalled) {
                callbackCalled = true
                try { cameraDevice?.close() } catch (e: Exception) {}
                stopBackgroundThread()
                callback.onImageSaved(f)
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            safeCall(null)
            return
        }

        startBackgroundThread()
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList.firstOrNull() ?: return safeCall(null)

            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(ImageFormat.JPEG)?.firstOrNull() ?: return safeCall(null)

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val buffer: ByteBuffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val file = File(context.filesDir, "snapshot_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { it.write(bytes) }
                            image.close()
                            safeCall(file)
                        } else {
                            safeCall(null)
                        }
                    } catch (e: Exception) {
                        safeCall(null)
                    }
                }, backgroundHandler)
            }

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureBuilder.addTarget(imageReader!!.surface)
                        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

                        // Add this line to compress the photo (e.g., 50%)
                        captureBuilder.set(CaptureRequest.JPEG_QUALITY, 80.toByte())

                        camera.createCaptureSession(listOf(imageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                                            super.onCaptureFailed(session, request, failure)
                                            safeCall(null)
                                        }
                                    }, backgroundHandler)
                                } catch (e: Exception) {
                                    safeCall(null)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                safeCall(null)
                            }
                        }, backgroundHandler)
                    } catch (e: Exception) {
                        safeCall(null)
                    }
                }

                override fun onClosed(camera: CameraDevice) {
                    super.onClosed(camera)
                    safeCall(null)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    safeCall(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    safeCall(null)
                }
            }, mainHandler)

        } catch (e: Exception) {
            safeCall(null)
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
        }
    }
}
