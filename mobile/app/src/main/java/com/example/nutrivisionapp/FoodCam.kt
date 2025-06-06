package com.example.nutrivisionapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.nutrivisionapp.api.FoodDetectionService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.android.material.snackbar.Snackbar

class FoodCam : AppCompatActivity() {

    private lateinit var imagePreview: android.widget.ImageView
    private var photoUri: Uri? = null
    private val IMAGE_DIRECTORY = "MyAppImages"
    private val PERMISSION_REQUEST_CODE = 2001
    private lateinit var foodDetectionService: FoodDetectionService

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

        // Initialize Retrofit
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        foodDetectionService = retrofit.create(FoodDetectionService::class.java)

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
                    R.id.food_journal -> {
                        startActivity(Intent(this@FoodCam, FoodJournal::class.java))
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
            photoUri?.let { uri ->
                detectFood(uri)
            } ?: run {
                Toast.makeText(this, "Please take or select an image first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun detectFood(imageUri: Uri) {
        // Show loading dialog
        val loadingDialog = AlertDialog.Builder(this)
            .setView(R.layout.loading_dialog)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageFile = File(getRealPathFromUri(imageUri))
                val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)

                val response = foodDetectionService.detectFood(imagePart)
                
                withContext(Dispatchers.Main) {
                    // Dismiss loading dialog
                    loadingDialog.dismiss()
                    
                    if (response.isSuccessful) {
                        response.body()?.let { result ->
                            val foodName = extractFoodName(result.prediction)
                            showPredictionDialog(foodName)
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("FoodCam", "Error response code: ${response.code()}")
                        Log.e("FoodCam", "Error response message: ${response.message()}")
                        Log.e("FoodCam", "Error body: $errorBody")
                        
                        AlertDialog.Builder(this@FoodCam)
                            .setTitle("Error Detecting Food")
                            .setMessage("Error Code: ${response.code()}\nError Details: $errorBody")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Dismiss loading dialog
                    loadingDialog.dismiss()
                    Toast.makeText(this@FoodCam, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getRealPathFromUri(uri: Uri): String {
        return when {
            uri.scheme == "content" -> {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val tempFile = File.createTempFile("temp_image", ".jpg", cacheDir)
                        tempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        tempFile.absolutePath
                    } ?: throw Exception("Could not open input stream")
                } catch (e: Exception) {
                    throw Exception("Error getting real path: ${e.message}")
                }
            }
            uri.scheme == "file" -> uri.path ?: throw Exception("Invalid file path")
            else -> throw Exception("Unsupported URI scheme: ${uri.scheme}")
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

    private fun showPredictionDialog(foodName: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Detected: $foodName")
            .setPositiveButton("Update food name") { _, _ ->
                showUpdateFoodNameDialog(foodName)
            }
            .setNegativeButton("Log in food journal") { dialogInterface, _ ->
                logFoodJournalEntry(foodName)
                dialogInterface.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun showUpdateFoodNameDialog(currentName: String) {
        val editText = android.widget.EditText(this)
        editText.setText(currentName)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Update Food Name")
            .setView(editText)
            .setPositiveButton("Save") { dialogInterface, _ ->
                val updatedName = editText.text.toString()
                dialogInterface.dismiss()
                showLogUpdatedFoodDialog(updatedName)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun showLogUpdatedFoodDialog(foodName: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Detected: $foodName")
            .setPositiveButton("Log in food journal") { dialogInterface, _ ->
                logFoodJournalEntry(foodName)
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun extractFoodName(prediction: String): String {
        // Example: "Detected: Apple (Confidence: 98.00%)"
        val regex = Regex("Detected: (.*?) \\(Confidence:", RegexOption.IGNORE_CASE)
        val match = regex.find(prediction)
        return match?.groups?.get(1)?.value?.trim() ?: prediction
    }

    private fun logFoodJournalEntry(foodName: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = user.uid
        val database = FirebaseDatabase.getInstance()
        val journalRef = database.reference.child("users").child(userId).child("food_journal")
        val entryId = journalRef.push().key ?: System.currentTimeMillis().toString()
        val entry = mapOf(
            "id" to entryId,
            "foodName" to foodName,
            "timestamp" to System.currentTimeMillis()
        )
        journalRef.child(entryId).setValue(entry)
            .addOnSuccessListener {
                Snackbar.make(findViewById(android.R.id.content), "Food logged in journal!", Snackbar.LENGTH_LONG)
                    .setAction("View Food Journal") {
                        startActivity(Intent(this, FoodJournal::class.java))
                    }
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to log food", Toast.LENGTH_SHORT).show()
            }
    }
}