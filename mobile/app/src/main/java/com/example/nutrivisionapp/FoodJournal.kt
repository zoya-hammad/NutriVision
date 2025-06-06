package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class FoodJournalEntry(var id: String = "", var foodName: String = "", val timestamp: Long = 0L)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        recyclerView = findViewById(R.id.food_journal_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FoodJournalAdapter(journalEntries, ::onEntryClick)
        recyclerView.adapter = adapter

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

    override fun onStart() {
        super.onStart()
        val userId = auth.currentUser?.uid ?: return
        val journalRef = database.reference.child("users").child(userId).child("food_journal")
        journalRef.orderByChild("timestamp").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                journalEntries.clear()
                for (entrySnap in snapshot.children) {
                    val entry = entrySnap.getValue(FoodJournalEntry::class.java)
                    if (entry != null) {
                        entry.id = entrySnap.key ?: ""
                        journalEntries.add(entry)
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error if needed
            }
        })
    }

    private fun onEntryClick(entry: FoodJournalEntry) {
        // TODO: Show dialog to edit or delete entry
    }
}

// TODO: Implement dialog for editing/deleting entries