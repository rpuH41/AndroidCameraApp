package com.example.cameraandroidapp

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.Image
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val cameraPermissionRequestCode = 100
    private lateinit var imageView: ImageView
    private val previewView : PreviewView = findViewById(R.id.previewView)
    private val imageCapture = ImageCapture.Builder()
        .setTargetRotation(previewView.display.rotation)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        val cameraButton: Button = findViewById(R.id.cameraButton)

        cameraButton.setOnClickListener {
            if (!isCameraAvailable()) {
                requestCameraPermission()
            }
            captureImage()
//            saveImage()
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), cameraPermissionRequestCode)
        } else {
            // Permission already granted
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            // Permission denied
            Toast.makeText(this, "Нет разрешения на использование камеры",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun isCameraAvailable(): Boolean {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        return cameraIds.isNotEmpty()
    }

    private fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build()
            val imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
//                preview.setSurfaceProvider = previewView.createSurfaceProvider(camera.cameraInfo)
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (exception: Exception) {
                // Handle camera setup errors
                Toast.makeText(this, "Ошибка при сьемке камерой",
                    Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val file = File(externalMediaDirs.first(), "image.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Image saved successfully
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle image capture errors
                }
            })
    }

    private fun saveImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val file = File(externalMediaDirs.first(), "image.jpg")
        FileOutputStream(file).use { output ->
            output.write(bytes)
        }
    }
}