package com.example.cameraandroidapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.cameraandroidapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var outputDirectory : File
    private lateinit var cameraExecutor : ExecutorService
    private lateinit var timePhoto : String
    private lateinit var pathPhoto : String
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (allPermissionGranted()) {
            Log.d(Constants.TAG, "We have all permissions")
            startCamera()
            getCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(this,
                Constants.REQUIRED_PERMISSIONS,
                Constants.REQUEST_CODE_PERMISSIONS)
        }

        binding.captureButton.setOnClickListener {
            takePhoto()
        }

        binding.galleryButton.setOnClickListener {
            Toast.makeText(this, "Пока не сделано", Toast.LENGTH_LONG).show()
        }
    }

    private fun getOutputDirectory() : File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {mFile ->
            File(mFile, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if(mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) ==
                    PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                startCamera()
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Permissions not granted by the user",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
                .also {mPreview -> mPreview.surfaceProvider = binding.previewView.surfaceProvider }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(Constants.TAG, "startCamera Fail: ", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        timePhoto = SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())
        val photoFile = File (outputDirectory, SimpleDateFormat(Constants.FILE_NAME_FORMAT,
            Locale.getDefault())
            .format(System.currentTimeMillis()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                pathPhoto = Uri.fromFile(photoFile).toString()
                val msgSaved = "Photo saved"
                Toast.makeText(this@MainActivity, "$msgSaved $pathPhoto", Toast.LENGTH_LONG)
                    .show()

                // Добавляем GPS-данные, если местоположение доступно
                currentLocation?.let { location ->
                    try {
                        val exif = ExifInterface(photoFile.path)
                        setGpsExifAttributes(exif, location)
                    } catch (e: IOException) {
                        Log.e("CameraXApp", "Failed to set GPS metadata", e)
                    }
                }

                // Добавляем изображение в медиабазу данных, чтобы оно отображалось в галерее
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                }

                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        photoFile.inputStream().copyTo(outputStream)
                    }
                }

                val msg = "Photo capture succeeded"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show() // Показываем сообщение об успешном захвате
                Log.d("CameraXApp", msg) // Логируем успешный захват изображения
            }

            override fun onError(e: ImageCaptureException) {
                Log.e(Constants.TAG, "Photo capture failed: ${e.message}", e)
            }
        })
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        currentLocation = location
                    } else {
                        Log.e("CameraXApp", "Location is null")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CameraXApp", "Failed to get location", e)
                }
        }
    }

    // Метод для преобразования координат в формат DMS
    private fun convertLatLongToDMS(coordinate: Double): String {
        val absolute = Math.abs(coordinate)
        val degrees = Math.floor(absolute)
        val minutes = Math.floor((absolute - degrees) * 60)
        val seconds = ((absolute - degrees - minutes / 60) * 3600 * 1000).toInt()
        return "${degrees.toInt()}/1,${minutes.toInt()}/1,$seconds/1000"
    }

    // Метод для установки GPS-данных в Exif метаданные
    private fun setGpsExifAttributes(exif: ExifInterface, location: Location) {
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertLatLongToDMS(location.latitude))
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude >= 0) "N" else "S")
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertLatLongToDMS(location.longitude))
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude >= 0) "E" else "W")
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, (location.altitude.toInt()).toString())
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (location.altitude >= 0) "0" else "1")
        exif.saveAttributes()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
