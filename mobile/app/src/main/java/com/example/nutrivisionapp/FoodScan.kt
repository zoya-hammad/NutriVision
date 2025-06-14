package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.common.util.concurrent.ListenableFuture

@androidx.camera.core.ExperimentalGetImage
class FoodScan : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imagePreview: ImageView
    private lateinit var txtIngredients: TextView
    private lateinit var txtAllergyStatus: TextView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var processedBarcodes = mutableSetOf<String>()

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            imagePreview.setImageURI(it)
            imagePreview.visibility = ImageView.VISIBLE
            previewView.visibility = PreviewView.GONE

            val image = InputImage.fromFilePath(this, it)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val code = barcode.rawValue
                        if (!code.isNullOrBlank() && code !in processedBarcodes) {
                            processedBarcodes.add(code)
                            fetchProductFromBarcode(code)
                        }
                    }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_foodscan)

        previewView = findViewById(R.id.previewView)
        imagePreview = findViewById(R.id.image_preview_barcode)
        txtIngredients = findViewById(R.id.txt_ingredients)
        txtAllergyStatus = findViewById(R.id.txt_allergy_status)

        previewView.visibility = PreviewView.GONE
        imagePreview.visibility = ImageView.GONE

        findViewById<MaterialButton>(R.id.btn_scan_barcode).setOnClickListener {
            startCamera()
        }

        findViewById<MaterialButton>(R.id.btn_upload_barcode).setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        val statusBar = findViewById<BottomNavigationView>(R.id.status)
        statusBar.selectedItemId = R.id.camera
        statusBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, HomeScreen::class.java)); finish(); true
                }
                R.id.camera -> {
                    startActivity(Intent(this, FoodCam::class.java)); finish(); true
                }
                R.id.food_journal -> {
                    startActivity(Intent(this, FoodJournal::class.java)); finish(); true
                }
                R.id.assistant -> {
                    startActivity(Intent(this, Assistant::class.java)); finish(); true
                }
                R.id.user -> {
                    startActivity(Intent(this, User::class.java)); finish(); true
                }
                else -> false
            }
        }
    }

    private fun startCamera() {
        imagePreview.visibility = ImageView.GONE
        previewView.visibility = PreviewView.VISIBLE

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        val scanner = BarcodeScanning.getClient()
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val code = barcode.rawValue
                                    if (!code.isNullOrBlank() && code !in processedBarcodes) {
                                        processedBarcodes.add(code)
                                        fetchProductFromBarcode(code)
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analyzer)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun fetchProductFromBarcode(barcode: String) {
        val url = "https://world.openfoodfacts.org/api/v0/product/$barcode.json"

        val request = Request.Builder().url(url).build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@FoodScan, "API call failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: "")
                if (json.optInt("status") == 1) {
                    val product = json.getJSONObject("product")
                    val rawIngredients = product.optString("ingredients_text_en", "")
                        .ifBlank { product.optString("ingredients_text", "") }

                    val cleanedIngredients = cleanIngredients(rawIngredients)

                    val allergensArray = product.optJSONArray("allergens_tags")
                    val productAllergens = mutableListOf<String>()
                    for (i in 0 until (allergensArray?.length() ?: 0)) {
                        productAllergens.add(allergensArray!!.getString(i).removePrefix("en:"))
                    }

                    runOnUiThread {
                        txtIngredients.text = "Ingredients:\n$cleanedIngredients"
                    }

                    checkUserAllergies(productAllergens, cleanedIngredients)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@FoodScan, "Product not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun checkUserAllergies(productAllergens: List<String>, ingredients: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("users/$uid/allergies")

        ref.get().addOnSuccessListener { snapshot ->
            val userAllergies = snapshot.getValue(String::class.java)
                ?.split(",", ";", "\n")
                ?.map { it.trim().lowercase() }
                ?: emptyList()

            val matched = userAllergies.filter {
                productAllergens.contains(it) || ingredients.lowercase().contains(it)
            }

            runOnUiThread {
                if (matched.isNotEmpty()) {
                    txtAllergyStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    txtAllergyStatus.text = "⚠️ This product contains: ${matched.joinToString(", ")}!"
                } else {
                    txtAllergyStatus.setTextColor(ContextCompat.getColor(this, R.color.green_dark))
                    txtAllergyStatus.text = "✅ Safe to consume! No mentioned allergen(s) found."
                }
            }
        }
    }


    // cleans the raw ingredients using regex
    fun cleanIngredients(raw: String): String {
        return raw
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .replace("•", ",")
            .replace(Regex("[;,]"), ", ")
            .replace(Regex(",\\s+,"), ", ")
            .trim()
    }

}
