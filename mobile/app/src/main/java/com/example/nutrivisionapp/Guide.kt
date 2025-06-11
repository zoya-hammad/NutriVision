package com.example.nutrivisionapp

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class Guide : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            // Set guide as selected
            selectedItemId = R.id.guide

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(android.content.Intent(this@Guide, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        startActivity(android.content.Intent(this@Guide, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.food_journal -> {
                        startActivity(android.content.Intent(this@Guide, FoodJournal::class.java))
                        finish()
                        true
                    }
                    R.id.assistant -> {
                        startActivity(android.content.Intent(this@Guide, Assistant::class.java))
                        finish()
                        true
                    }
                    R.id.user -> {
                        startActivity(android.content.Intent(this@Guide, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }

        // Add guide sections
        val guideContainer = findViewById<LinearLayout>(R.id.guide_container)
        
        // Home Screen Section
        addGuideSection(guideContainer, "Home Screen", """
            • Quick access to all app features
            • Emergency contact buttons for doctor and hospital
            • Easy navigation to all sections
            • View your daily summary
        """.trimIndent())

        // Food Camera Section
        addGuideSection(guideContainer, "Food Camera", """
            • Take photos of your food
            • Get instant nutritional analysis
            • View detailed food information
            • Save food entries to your journal
        """.trimIndent())

        // Food Journal Section
        addGuideSection(guideContainer, "Food Journal", """
            • Track your daily food intake
            • View nutritional history
            • Monitor your eating patterns
            • Export your food diary
        """.trimIndent())

        // Assistant Section
        addGuideSection(guideContainer, "Nutritional Assistant", """
            • Get recipe suggestions
            • View glycemic load analysis
            • Save favorite recipes
            • Get personalized recommendations
        """.trimIndent())

        // My Recipes Section
        addGuideSection(guideContainer, "My Recipes", """
            • View all your saved recipes
            • Check glycemic load details
            • Review ingredients and instructions
            • Access nutritional information
        """.trimIndent())

        // User Profile Section
        addGuideSection(guideContainer, "User Profile", """
            • Manage personal information
            • Set dietary preferences
            • Update emergency contacts
            • View saved recipes
        """.trimIndent())
    }

    private fun addGuideSection(container: LinearLayout, title: String, content: String) {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 8f
            cardElevation = 2f
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(resources.getColor(R.color.green_dark, theme))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val contentView = TextView(this).apply {
            text = content
            textSize = 14f
            setLineSpacing(0f, 1.2f)
        }

        cardContent.addView(titleView)
        cardContent.addView(contentView)
        card.addView(cardContent)
        container.addView(card)
    }
} 