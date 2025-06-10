package com.example.nutrivisionapp

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

object StreakCounter {

    fun updateStreak(context: Context): String {
        val prefs = context.getSharedPreferences("habitPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val lastDate = prefs.getLong("lastDate", 0L)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val oneDayMillis = 24 * 60 * 60 * 1000L
        val diff = today - lastDate
        var streak = prefs.getInt("streak", 0)

        when {
            lastDate == 0L -> {
                streak = 1
            }
            diff == oneDayMillis -> {
                streak += 1
            }
            diff > oneDayMillis -> {
                streak = 1
            }
            diff == 0L -> {
                return "You've already logged today!\nStreak: $streak days in a row!"
            }
        }

        editor.putInt("streak", streak)
        editor.putLong("lastDate", today)
        editor.apply()

        // Sync to Firebase
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUser.uid)
                .child("streakData")

            val streakData = mapOf(
                "streak" to streak,
                "lastLoggedDate" to today
            )

            userRef.setValue(streakData)
        }

        //  motivation messages
        val motivation = when (streak) {
            2 -> "You're getting into a rhythm! 💪"
            3 -> "3 days strong! Small steps, big results. 💪"
            4 -> "Consistency is your superpower. ⚡"
            5 -> "Halfway to 10! You’re doing amazing. ✨"
            6 -> "You're on fire! 🔥 Don't break the streak!"
            7 -> "This habit is sticking — nice work!"
            8 -> "You’re proving what dedication looks like."
            9 -> "One day at a time. You’re building something great.💪"
            10 -> "10 days strong! Incredible dedication! 🎉"
            else -> null
        }

        return if (motivation != null)
            "You've logged meals $streak days in a row!\n$motivation"
        else
            "You've logged meals $streak days in a row!\n Keep it up!"
    }
}
