package com.example.nutrivisionapp


import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class Changepass : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changepass)

        auth = Firebase.auth
        setupPasswordChange()
    }



    private fun setupPasswordChange() {
        findViewById<MaterialButton>(R.id.btn_update_password).setOnClickListener {
            changePassword()
        }
    }

    private fun changePassword() {
        val currentPassword = findViewById<TextInputEditText>(R.id.et_current_password).text.toString()
        val newPassword = findViewById<TextInputEditText>(R.id.et_new_password).text.toString()
        val confirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password).text.toString()

        if (!validateInputs(currentPassword, newPassword, confirmPassword)) {
            return
        }

        val user = auth.currentUser
        val credential = EmailAuthProvider.getCredential(user?.email ?: "", currentPassword)

        user?.reauthenticate(credential)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateInputs(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Boolean {
        if (currentPassword.isEmpty()) {
            findViewById<TextInputEditText>(R.id.et_current_password).error = "Required"
            return false
        }

        if (newPassword.isEmpty()) {
            findViewById<TextInputEditText>(R.id.et_new_password).error = "Required"
            return false
        }

        if (newPassword.length < 6) {
            findViewById<TextInputEditText>(R.id.et_new_password).error = "Minimum 8 characters"
            return false
        }

        if (confirmPassword != newPassword) {
            findViewById<TextInputEditText>(R.id.et_confirm_password).error = "Passwords don't match"
            return false
        }

        return true
    }
}