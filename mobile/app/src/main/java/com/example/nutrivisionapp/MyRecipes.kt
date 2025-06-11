package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.Gson

data class SavedRecipe(
    val id: String = "",
    val title: String = "",
    val glycemicLoad: Double = 0.0,
    val ingredients: List<String> = listOf(),
    val instructions: List<String> = listOf(),
    val glAnalysis: Map<String, String> = mapOf(),
    val savedAt: Long = System.currentTimeMillis()
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", 0.0, listOf(), listOf(), mapOf(), 0L)
}

class RecipeAdapter(
    private val recipes: List<SavedRecipe>,
    private val onClick: (SavedRecipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(recipes[position], onClick)
    }

    override fun getItemCount() = recipes.size

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.recipe_title)
        private val glycemicLoadView: TextView = view.findViewById(R.id.glycemic_load)
        private val savedDateView: TextView = view.findViewById(R.id.saved_date)

        fun bind(recipe: SavedRecipe, onClick: (SavedRecipe) -> Unit) {
            titleView.text = recipe.title
            glycemicLoadView.text = "GL: ${recipe.glycemicLoad}"
            
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            savedDateView.text = "Saved: ${sdf.format(Date(recipe.savedAt))}"
            
            itemView.setOnClickListener { onClick(recipe) }
        }
    }
}

class MyRecipes : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecipeAdapter
    private val savedRecipes = mutableListOf<SavedRecipe>()
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_recipes)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        recyclerView = findViewById(R.id.recipes_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecipeAdapter(savedRecipes, ::onRecipeClick)
        recyclerView.adapter = adapter

        setupItemTouchHelper()
        loadSavedRecipes()

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            selectedItemId = R.id.home
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(Intent(this@MyRecipes, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        startActivity(Intent(this@MyRecipes, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.food_journal -> {
                        startActivity(Intent(this@MyRecipes, FoodJournal::class.java))
                        finish()
                        true
                    }
                    R.id.assistant -> {
                        startActivity(Intent(this@MyRecipes, Assistant::class.java))
                        finish()
                        true
                    }
                    R.id.user -> {
                        startActivity(Intent(this@MyRecipes, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupItemTouchHelper() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val recipe = savedRecipes[position]
                showDeleteConfirmation(recipe)
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = android.graphics.Paint()
                if (dX > 0) { // Swiping right (delete)
                    paint.color = android.graphics.Color.RED
                    val background = android.graphics.RectF(
                        itemView.left.toFloat(), itemView.top.toFloat(),
                        itemView.left + dX, itemView.bottom.toFloat()
                    )
                    c.drawRect(background, paint)
                    paint.color = android.graphics.Color.WHITE
                    paint.textSize = 40f
                    c.drawText("Delete", itemView.left + 50f, itemView.top + itemView.height / 2f, paint)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun showDeleteConfirmation(recipe: SavedRecipe) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recipe")
            .setMessage("Are you sure you want to delete this recipe?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecipe(recipe)
            }
            .setNegativeButton("Cancel") { _, _ ->
                adapter.notifyDataSetChanged()
            }
            .show()
    }

    private fun deleteRecipe(recipe: SavedRecipe) {
        val userId = auth.currentUser?.uid ?: return
        database.reference
            .child("users")
            .child(userId)
            .child("saved_recipes")
            .child(recipe.id)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Recipe deleted", Toast.LENGTH_SHORT).show()
                loadSavedRecipes()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete recipe", Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            }
    }

    private fun loadSavedRecipes() {
        val userId = auth.currentUser?.uid ?: return
        database.reference
            .child("users")
            .child(userId)
            .child("saved_recipes")
            .get()
            .addOnSuccessListener { snapshot ->
                savedRecipes.clear()
                snapshot.children.forEach { child ->
                    child.getValue(SavedRecipe::class.java)?.let { recipe ->
                        savedRecipes.add(recipe)
                    }
                }
                savedRecipes.sortByDescending { it.savedAt }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load recipes", Toast.LENGTH_SHORT).show()
            }
    }

    private fun onRecipeClick(recipe: SavedRecipe) {
        val intent = Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra("recipe_data", Gson().toJson(recipe))
        }
        startActivity(intent)
    }
}