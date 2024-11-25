package com.example.cameraandroidapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Запрашиваем разрешения на использование камеры и местоположения
        val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true && permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                startCamera() // Запускаем камеру, если разрешения предоставлены
                getCurrentLocation() // Получаем текущее местоположение
            } else {
                Toast.makeText(this, "Camera or location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        // Проверяем, есть ли разрешения на использование камеры и местоположения
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startCamera() // Если разрешения уже есть, запускаем камеру
            getCurrentLocation() // Получаем текущее местоположение
        } else {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)) // Иначе запрашиваем разрешения
        }

        // Находим кнопку захвата изображения и устанавливаем обработчик нажатия
        val captureButton: Button = findViewById(R.id.captureButton)
        captureButton.setOnClickListener { takePhoto() }
    }

    // Метод для инициализации и запуска камеры
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Получаем провайдер камеры
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Создаем объект для захвата изображения
            imageCapture = ImageCapture.Builder().build()

            // Выбираем заднюю камеру по умолчанию
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Отвязываем все привязанные случаи использования и привязываем новый случай использования для захвата изображения
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraXApp", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Метод для получения текущего местоположения
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

    // Метод для захвата изображения
    private fun takePhoto() {
        val photoFile = File(externalMediaDirs.firstOrNull(), "IMG_${System.currentTimeMillis()}.jpg")

        // Опции для вывода файла
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Захватываем изображение
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraXApp", "Photo capture failed: ${exc.message}", exc) // Логируем ошибку при захвате изображения
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
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
        })
    }
}
