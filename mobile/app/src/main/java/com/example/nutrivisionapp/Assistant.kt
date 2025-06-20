package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.nutrivisionapp.api.Recipe
import com.example.nutrivisionapp.api.RecipeRequest
import com.example.nutrivisionapp.api.RecipeClient
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class Assistant : AppCompatActivity() {
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button
    private lateinit var messagesContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView

    private lateinit var recipeCard: View
    private lateinit var ingredientsContainer: LinearLayout
    private lateinit var instructionsContainer: LinearLayout

    // Speech recognition launcher
    private val speechRecognizer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.get(0)?.let { spokenText ->
                // Set the spoken text to the input field
                messageInput.setText(spokenText)
                // Automatically trigger the send button
                sendButton.performClick()
            }
        } else {
            Toast.makeText(this, "Speech recognition failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)

        // Initialize views
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        micButton = findViewById(R.id.mic_button)
        messagesContainer = findViewById(R.id.messages_container)
        chatScrollView = findViewById(R.id.chat_scroll_view)

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            // Set assistant as selected
            selectedItemId = R.id.assistant

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(Intent(this@Assistant, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        startActivity(Intent(this@Assistant, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.food_journal -> {
                        startActivity(Intent(this@Assistant, FoodJournal::class.java))
                        finish()
                        true
                    }
                    R.id.assistant -> {
                        // Already here
                        true
                    }
                    R.id.user -> {
                        startActivity(Intent(this@Assistant, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }

        // Set up send button
        sendButton.setOnClickListener {
            val message = messageInput.text.toString()
            if (message.isNotEmpty()) {
                addUserMessage(message)
                messageInput.text?.clear()
                fetchRecipe(message)
            }
        }

        // Set up mic button for voice input
        micButton.setOnClickListener {
            startVoiceRecognition()
        }
    }

    private fun startVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchRecipe(query: String) {
        // Show loading dialog
        val loadingDialog = AlertDialog.Builder(this)
            .setView(R.layout.loading_dialog)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        val request = RecipeRequest(query)
        RecipeClient.recipeApi.getRecipe(request).enqueue(object : Callback<Recipe> {
            override fun onResponse(call: Call<Recipe>, response: Response<Recipe>) {
                // Dismiss loading dialog
                loadingDialog.dismiss()
                
                if (response.isSuccessful) {
                    response.body()?.let { recipe ->
                        showRecipeResponse(recipe)
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    val errorMessage = """
                        Failed to fetch recipe. 
                        Error Code: ${response.code()}
                        Error Message: ${response.message()}
                        Error Body: $errorBody
                    """.trimIndent()
                    showErrorMessage(errorMessage)
                }
            }

            override fun onFailure(call: Call<Recipe>, t: Throwable) {
                // Dismiss loading dialog
                loadingDialog.dismiss()
                
                val errorMessage = """
                    Network error occurred.
                    Error Type: ${t.javaClass.simpleName}
                    Error Message: ${t.message}
                    Stack Trace: ${t.stackTraceToString()}
                """.trimIndent()
                showErrorMessage(errorMessage)
            }
        })
    }

    private fun showRecipeResponse(recipe: Recipe) {
        recipeCard = LayoutInflater.from(this).inflate(R.layout.item_recipe_card, messagesContainer, false)
        
        // Set recipe title
        recipeCard.findViewById<TextView>(R.id.recipe_title).text = recipe.title
        
        // Set glycemic load
        val glycemicLoadText = recipeCard.findViewById<TextView>(R.id.glycemic_load)
        val glCategory = recipe.gl_analysis["category"] as? String ?: getGlycemicLoadCategory(recipe.glycemic_load)
        val glRecommendation = recipe.gl_analysis["recommendation"] as? String ?: ""
        glycemicLoadText.text = "Glycemic Load: ${String.format("%.1f", recipe.glycemic_load)} ($glCategory)\n$glRecommendation"
        
        // Add ingredients
        ingredientsContainer = recipeCard.findViewById(R.id.ingredients_container)
        recipe.ingredients.forEach { ingredient ->
            val ingredientView = TextView(this).apply {
                text = "• ${ingredient.quantity} ${ingredient.unit} ${ingredient.ingredient}"
                setPadding(0, 4, 0, 4)
            }
            ingredientsContainer.addView(ingredientView)
        }
        
        // Add instructions
        instructionsContainer = recipeCard.findViewById(R.id.instructions_container)
        recipe.instructions.forEachIndexed { index, instruction ->
            val instructionView = TextView(this).apply {
                text = "${index + 1}. $instruction"
                setPadding(0, 4, 0, 4)
            }
            instructionsContainer.addView(instructionView)
        }

        // Set up save button
        recipeCard.findViewById<MaterialButton>(R.id.btn_save_recipe).setOnClickListener {
            saveRecipe(recipe)
        }
        
        messagesContainer.addView(recipeCard)
        scrollToBottom()
    }

    private fun saveRecipe(recipe: Recipe) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Please sign in to save recipes", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = user.uid
        val database = FirebaseDatabase.getInstance()
        val recipesRef = database.reference.child("users").child(userId).child("saved_recipes")
        val recipeId = recipesRef.push().key ?: System.currentTimeMillis().toString()

        val savedRecipe = SavedRecipe(
            id = recipeId,
            title = recipe.title,
            glycemicLoad = recipe.glycemic_load,
            ingredients = recipe.ingredients.map { "${it.quantity} ${it.unit} ${it.ingredient}" },
            instructions = recipe.instructions,
            glAnalysis = recipe.gl_analysis.mapValues { it.value.toString() },
            savedAt = System.currentTimeMillis()
        )

        recipesRef.child(recipeId).setValue(savedRecipe)
            .addOnSuccessListener {
                Toast.makeText(this, "Recipe saved!", Toast.LENGTH_SHORT).show()
                Snackbar.make(findViewById(android.R.id.content), "Recipe saved!", Snackbar.LENGTH_LONG)
                    .setAction("View Saved Recipes") {
                        startActivity(Intent(this, MyRecipes::class.java))
                    }
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save recipe", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getGlycemicLoadCategory(load: Double): String {
        return when {
            load < 10 -> "Low"
            load < 20 -> "Medium"
            else -> "High"
        }
    }

    private fun showErrorMessage(message: String) {
        val errorView = TextView(this).apply {
            text = message
            setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }
        messagesContainer.addView(errorView)
        scrollToBottom()
    }

    fun onSuggestedQueryClick(view: View) {
        if (view is Chip) {
            val query = view.text.toString()
            addUserMessage(query)
            fetchRecipe(query)
        }
    }

    private fun addUserMessage(message: String) {
        val messageView = TextView(this).apply {
            text = message
            setPadding(16, 8, 16, 8)
            setBackgroundResource(R.drawable.message_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
                gravity = android.view.Gravity.END
            }
        }

        messagesContainer.addView(messageView)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        chatScrollView.post {
            chatScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}