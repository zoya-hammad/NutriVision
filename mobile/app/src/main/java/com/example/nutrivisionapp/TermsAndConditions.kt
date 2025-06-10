package com.example.nutrivisionapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class TermsAndConditions : AppCompatActivity() {

    private lateinit var agreeButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_and_conditions)

        agreeButton = findViewById(R.id.btn_agree)

        agreeButton.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
    }
} 