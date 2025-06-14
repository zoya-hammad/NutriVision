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

        // streak logic
        if (lastDate == 0L) {
            streak = 1
        } else if (diff == oneDayMillis) {
            streak += 1
        } else if (diff > oneDayMillis) {
            streak = 1
        }
        // If diff == 0L, streak stays the same (still today)

        // Save updated streak and date
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

        // Return message with motivation
        val motivation = getMotivation(streak)
        return if (motivation != null)
            "Streak: $streak \n$motivation"
        else
            "Streak: $streak \nKeep it up!"
    }

    private fun getMotivation(streak: Int): String? {
        return when (streak) {
            1 -> "You've logged meals today!\nKeep it up to develop a streak!"
            2 -> "You're getting into a rhythm! 💪"
            3 -> "3 days strong! Small steps, big results. 💪"
            4 -> "Consistency is your superpower. ⚡"
            5 -> "Halfway to 10! You're doing amazing. ✨"
            6 -> "You're on fire! 🔥 Don't break the streak!"
            7 -> "This habit is sticking — nice work!"
            8 -> "You're proving what dedication looks like."
            9 -> "One day at a time. You're building something great. 💪"
            10 -> "10 days strong! Incredible dedication! 🎉"
            else -> null
        }
    }
}
