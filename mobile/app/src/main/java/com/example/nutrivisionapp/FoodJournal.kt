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
import com.example.nutrivisionapp.api.FoodAnalysisService
import com.example.nutrivisionapp.api.FoodAnalysis
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class FoodAnalysis(
    val calories: Int? = null,
    val glycemicLoad: Int? = null,
    val advice: String? = null
) {
    // No-arg constructor for Firebase
    constructor() : this(null, null, null)
}

data class FoodJournalEntry(
    var id: String = "",
    var foodName: String = "",
    var description: String = "",
    var timestamp: Long = 0L,
    var analysis: FoodAnalysis? = null
)

class FoodJournalAdapter(
    private val entries: List<FoodJournalEntry>,
    private val onClick: (FoodJournalEntry) -> Unit
) : RecyclerView.Adapter<FoodJournalAdapter.FoodJournalViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodJournalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return FoodJournalViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: FoodJournalViewHolder, position: Int) {
        holder.bind(entries[position], onClick)
    }

    override fun getItemCount() = entries.size

    class FoodJournalViewHolder(private val viewGroup: ViewGroup) : RecyclerView.ViewHolder(viewGroup) {
        fun bind(entry: FoodJournalEntry, onClick: (FoodJournalEntry) -> Unit) {
            val foodNameView = viewGroup.findViewById<TextView>(android.R.id.text1)
            val timeView = viewGroup.findViewById<TextView>(android.R.id.text2)
            foodNameView.text = entry.foodName
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            timeView.text = sdf.format(Date(entry.timestamp))
            viewGroup.setOnClickListener { onClick(entry) }
        }
    }
}

class FoodJournal : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FoodJournalAdapter
    private val journalEntries = mutableListOf<FoodJournalEntry>()
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var foodAnalysisService: FoodAnalysisService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food_journal)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        foodAnalysisService = FoodAnalysisService(this)

        recyclerView = findViewById(R.id.food_journal_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FoodJournalAdapter(journalEntries, ::onEntryClick)
        recyclerView.adapter = adapter

        setupItemTouchHelper()

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            selectedItemId = R.id.food_journal
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(Intent(this@FoodJournal, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        startActivity(Intent(this@FoodJournal, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.food_journal -> true
                    R.id.assistant -> {
                        startActivity(Intent(this@FoodJournal, Assistant::class.java))
                        finish()
                        true
                    }
                    R.id.user -> {
                        startActivity(Intent(this@FoodJournal, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupItemTouchHelper() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val entry = journalEntries[position]

                when (direction) {
                    ItemTouchHelper.LEFT -> showEditDialog(entry)
                    ItemTouchHelper.RIGHT -> showDeleteConfirmation(entry)
                }
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
                } else if (dX < 0) { // Swiping left (edit)
                    paint.color = android.graphics.Color.BLUE
                    val background = android.graphics.RectF(
                        itemView.right + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat()
                    )
                    c.drawRect(background, paint)
                    paint.color = android.graphics.Color.WHITE
                    paint.textSize = 40f
                    c.drawText("Edit", itemView.right - 200f, itemView.top + itemView.height / 2f, paint)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun showEditDialog(entry: FoodJournalEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_food_journal_entry, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        lateinit var foodNameInput: TextInputEditText
        lateinit var descriptionInput: TextInputEditText
        lateinit var analysisCard: View
        lateinit var caloriesText: TextView
        lateinit var glycemicLoadText: TextView
        lateinit var adviceText: TextView

        foodNameInput = dialogView.findViewById(R.id.food_name_input)
        descriptionInput = dialogView.findViewById(R.id.food_description_input)
        analysisCard = dialogView.findViewById(R.id.analysis_card)
        caloriesText = dialogView.findViewById(R.id.calories_text)
        glycemicLoadText = dialogView.findViewById(R.id.glycemic_load_text)
        adviceText = dialogView.findViewById(R.id.advice_text)

        // Set current values
        foodNameInput.setText(entry.foodName)
        descriptionInput.setText(entry.description)

        // Show analysis if available
        entry.analysis?.let { analysis ->
            analysisCard.visibility = View.VISIBLE
            caloriesText.text = "Calories: ${analysis.calories}"
            glycemicLoadText.text = "Glycemic Load: ${when(analysis.glycemicLoad) {
                1 -> "Low"
                2 -> "Medium"
                3 -> "High"
                else -> "Unknown"
            }}"
            adviceText.text = analysis.advice
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_analyze).setOnClickListener {
            val foodName = foodNameInput.text.toString()
            val description = descriptionInput.text.toString()

            if (foodName.isNotEmpty()) {
                analyzeFood(foodName, description, entry, dialogView)
            } else {
                Toast.makeText(this, "Food name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            val newName = foodNameInput.text.toString()
            val newDescription = descriptionInput.text.toString()

            if (newName.isNotEmpty()) {
                updateEntry(entry.id, newName, newDescription, entry.analysis)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Food name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun analyzeFood(
        foodName: String,
        description: String,
        entry: FoodJournalEntry,
        dialogView: View
    ) {
        val analysisCard = dialogView.findViewById<View>(R.id.analysis_card)
        val caloriesText = dialogView.findViewById<TextView>(R.id.calories_text)
        val glycemicLoadText = dialogView.findViewById<TextView>(R.id.glycemic_load_text)
        val adviceText = dialogView.findViewById<TextView>(R.id.advice_text)
        val analyzeButton = dialogView.findViewById<MaterialButton>(R.id.btn_analyze)

        analyzeButton.isEnabled = false
        analyzeButton.text = "Analyzing..."

        val userId = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val userAge = snapshot.child("age").getValue(String::class.java)
                val dietaryRestrictions = snapshot.child("dietaryRestrictions").getValue(String::class.java)
                val allergies = snapshot.child("allergies").getValue(String::class.java)

                // call the backend using coroutine for network
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    try {
                        val result = foodAnalysisService.analyzeFood(
                            foodName,
                            description,
                            userAge,
                            dietaryRestrictions,
                            allergies
                        )
                        result.fold(
                            onSuccess = { analysis ->
                                entry.analysis = analysis
                                analysisCard.visibility = View.VISIBLE
                                caloriesText.text = "Calories: ${analysis.calories}"
                                glycemicLoadText.text = "Glycemic Load: " + when (analysis.glycemicLoad) {
                                    1 -> "Low"
                                    2 -> "Medium"
                                    3 -> "High"
                                    else -> "Unknown"
                                }
                                adviceText.text = analysis.advice
                            },
                            onFailure = { error ->
                                Toast.makeText(
                                    this@FoodJournal,
                                    "Analysis failed: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@FoodJournal,
                            "Analysis failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        analyzeButton.isEnabled = true
                        analyzeButton.text = "Analyze"
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(this@FoodJournal, "Failed to load user data", Toast.LENGTH_SHORT).show()
                analyzeButton.isEnabled = true
                analyzeButton.text = "Analyze"
            }
        })
    }

    private fun showDeleteConfirmation(entry: FoodJournalEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this entry?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEntry(entry.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEntry(entryId: String, newName: String, newDescription: String, analysis: com.example.nutrivisionapp.api.FoodAnalysis?) {
        val userId = auth.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "foodName" to newName,
            "description" to newDescription
        )
        analysis?.let {
            updates["analysis"] = mapOf(
                "calories" to it.calories,
                "glycemicLoad" to it.glycemicLoad,
                "advice" to it.advice
            )
        }
        android.util.Log.d("FoodJournal", "Updating entry $entryId with: $updates")
        database.reference.child("users").child(userId).child("food_journal").child(entryId)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Entry updated", Toast.LENGTH_SHORT).show()
                android.util.Log.d("FoodJournal", "Entry $entryId updated successfully.")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update entry: ${e.message}", Toast.LENGTH_SHORT).show()
                android.util.Log.e("FoodJournal", "Failed to update entry $entryId", e)
            }
    }

    private fun deleteEntry(entryId: String) {
        val userId = auth.currentUser?.uid ?: return
        database.reference.child("users").child(userId).child("food_journal").child(entryId)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete entry", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onStart() {
        super.onStart()
        val userId = auth.currentUser?.uid ?: return
        val journalRef = database.reference.child("users").child(userId).child("food_journal")
        journalRef.orderByChild("timestamp").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                try {
                    journalEntries.clear()
                    for (entrySnap in snapshot.children) {
                        val entry = entrySnap.getValue(FoodJournalEntry::class.java)
                        if (entry != null) {
                            entry.id = entrySnap.key ?: ""
                            // parse analysis
                            val analysisMap = entrySnap.child("analysis").value as? Map<*, *>
                            if (analysisMap != null) {
                                entry.analysis = com.example.nutrivisionapp.api.FoodAnalysis(
                                    calories = (analysisMap["calories"] as? Long)?.toInt() ?: 0,
                                    glycemicLoad = (analysisMap["glycemicLoad"] as? Long)?.toInt() ?: 0,
                                    advice = analysisMap["advice"] as? String ?: ""
                                )
                            } else {
                                entry.analysis = null
                            }
                            journalEntries.add(entry)
                        }
                    }
                    journalEntries.reverse()
                    adapter.notifyDataSetChanged()
                    android.util.Log.d("FoodJournal", "Loaded ${journalEntries.size} entries.")
                } catch (e: Exception) {
                    android.util.Log.e("FoodJournal", "Error parsing journal entries", e)
                    // Show error in a dialog for full visibility
                    androidx.appcompat.app.AlertDialog.Builder(this@FoodJournal)
                        .setTitle("Error loading entries")
                        .setMessage(e.stackTraceToString())
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(this@FoodJournal, "Failed to load entries", Toast.LENGTH_SHORT).show()
                android.util.Log.e("FoodJournal", "Database error: ${error.message}")
            }
        })
    }

    private fun onEntryClick(entry: FoodJournalEntry) {
        showEditDialog(entry)
    }
}
