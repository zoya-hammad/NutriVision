package com.example.nutrivisionapp

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson

class RecipeDetailActivity : AppCompatActivity() {
    private lateinit var recipeCard: View
    private lateinit var ingredientsContainer: LinearLayout
    private lateinit var instructionsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)

        // Get the recipe data from the intent
        val recipeJson = intent.getStringExtra("recipe_data") ?: return
        val recipe = Gson().fromJson(recipeJson, SavedRecipe::class.java)
        
        // Display the recipe
        showRecipeResponse(recipe)
    }

    private fun showRecipeResponse(recipe: SavedRecipe) {
        recipeCard = layoutInflater.inflate(R.layout.item_recipe_card, findViewById(R.id.recipe_container), false)
        
        // Set recipe title
        recipeCard.findViewById<TextView>(R.id.recipe_title).text = recipe.title
        
        // Set glycemic load
        val glycemicLoadText = recipeCard.findViewById<TextView>(R.id.glycemic_load)
        val glCategory = recipe.glAnalysis["category"] ?: getGlycemicLoadCategory(recipe.glycemicLoad)
        val glRecommendation = recipe.glAnalysis["recommendation"] ?: ""
        glycemicLoadText.text = "Glycemic Load: ${String.format("%.1f", recipe.glycemicLoad)} ($glCategory)\n$glRecommendation"
        
        // Add ingredients
        ingredientsContainer = recipeCard.findViewById(R.id.ingredients_container)
        recipe.ingredients.forEach { ingredient ->
            val ingredientView = TextView(this).apply {
                text = "• ${ingredient}"
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

        // Hide the save button
        recipeCard.findViewById<View>(R.id.btn_save_recipe).visibility = View.GONE
        
        // Add the recipe card to the container
        findViewById<LinearLayout>(R.id.recipe_container).addView(recipeCard)
    }

    private fun getGlycemicLoadCategory(load: Double): String {
        return when {
            load < 10 -> "Low"
            load < 20 -> "Medium"
            else -> "High"
        }
    }
} 