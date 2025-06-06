package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText

class Assistant : AppCompatActivity() {
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button
    private lateinit var messagesContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView

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
                addMessageToChat(message)
                messageInput.text?.clear()
            }
        }

        // Mic button (placeholder for now)
        micButton.setOnClickListener {
            // Will implement voice input later
        }
    }

    private fun addMessageToChat(message: String) {
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