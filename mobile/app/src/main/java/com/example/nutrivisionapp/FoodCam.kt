package com.example.nutrivisionapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FoodCam : AppCompatActivity() {

    private lateinit var imagePreview: android.widget.ImageView
    private var photoUri: Uri? = null
    private val IMAGE_DIRECTORY = "MyAppImages"

    private val PERMISSION_REQUEST_CODE = 2001

    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    // Register for image selection result
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            imagePreview.setImageURI(it)
            photoUri = it

            // Save URI reference if needed
            val sharedPref = getPreferences(MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("last_image_uri", it.toString())
                apply()
            }
        }
    }

    // Register for camera capture result
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                imagePreview.setImageURI(uri)
            }
        }
    }

    // Register for permission requests
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionRationaleDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_foodcam)

        imagePreview = findViewById(R.id.image_preview)
        val btnUpload: MaterialButton = findViewById(R.id.btn_upload)
        val btnCamera: MaterialButton = findViewById(R.id.btn_camera)
        val btnDetectFood: MaterialButton = findViewById(R.id.btn_detect_food)

        // Load previous image if exists
        val sharedPref = getPreferences(MODE_PRIVATE)
        val lastImagePath = sharedPref.getString("last_image_path", null)
        lastImagePath?.let {
            val imageFile = File(it)
            if (imageFile.exists()) {
                photoUri = FileProvider.getUriForFile(this, "$packageName.provider", imageFile)
                imagePreview.setImageURI(photoUri)
            }
        }

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            // Set camera as selected
            selectedItemId = R.id.camera

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(Intent(this@FoodCam, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        // Already here
                        true
                    }
                    R.id.progress -> {
                        startActivity(Intent(this@FoodCam, Progress::class.java))
                        finish()
                        true
                    }
                    R.id.assistant -> {
                        startActivity(Intent(this@FoodCam, Assistant::class.java))
                        finish()
                        true
                    }
                    R.id.user -> {
                        startActivity(Intent(this@FoodCam, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }

        if (!hasPermissions()) {
            requestPermissions()
        }

        btnUpload.setOnClickListener {
            if (hasPermissions()) openGallery() else requestPermissions()
        }

        btnCamera.setOnClickListener {
            if (hasPermissions()) openCamera() else requestPermissions()
        }

        btnDetectFood.setOnClickListener {
            Toast.makeText(this, "Food detection feature coming soon!", Toast.LENGTH_SHORT).show()
        }

    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun openCamera() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(
            "IMG_${timestamp}_",
            ".jpg",
            storageDir
        )

        // Create and store the URI safely
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", imageFile)
        photoUri = uri

        // Save the path for persistence
        val sharedPref = getPreferences(MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("last_image_path", imageFile.absolutePath)
            apply()
        }

        // Launch camera using the Activity Result API with the local non-null URI
        cameraLauncher.launch(uri)
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Camera and storage permissions are needed to capture and select images")
            .setPositiveButton("Retry") { _, _ -> requestPermissions() }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
}