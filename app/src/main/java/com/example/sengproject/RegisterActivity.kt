package com.example.sengproject

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.util.Log
import android.content.Intent

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException

class RegisterActivity : AppCompatActivity() {

    // Firebase Auth instance
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        val loginButton: Button = findViewById(R.id.createAccountButton)
        loginButton.setOnClickListener {
            // Start LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        val createButton: Button = findViewById(R.id.loginButton)
        createButton.setOnClickListener {
            val email = findViewById<EditText>(R.id.editTextTextEmailAddress).text.toString().trim()
            val password = findViewById<EditText>(R.id.editTextTextPassword).text.toString().trim()
            val rePassword = findViewById<EditText>(R.id.editTextTextRePassword).text.toString().trim()

            if (validateForm(email, password, rePassword)) {
                createAccount(email, password)
            } else {
                Toast.makeText(this, "Please check your inputs.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateForm(email: String, password: String, rePassword: String): Boolean {
        // Check if the email is valid
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email address.", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check if the password meets your criteria
        // Example: at least 6 characters, at least one number
        val passwordPattern = "^(?=.*[0-9]).{6,}$"
        if (!password.matches(passwordPattern.toRegex())) {
            Toast.makeText(this, "Password must be at least 6 characters and contain at least one number.", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check if password and rePassword match
        if (password != rePassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun createAccount(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "createUserWithEmail:success")
                    val user = auth.currentUser
                    val intent = Intent(this@RegisterActivity, HubActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // If sign in fails, display a message to the user.
                    if (task.exception is FirebaseAuthUserCollisionException) {
                        // This exception is thrown if the email is already in use.
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        Toast.makeText(baseContext, "Email already in use.",
                            Toast.LENGTH_SHORT).show()
                    } else {
                        // Other types of exceptions could be handled here
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        Toast.makeText(baseContext, "Authentication failed.",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    companion object {
        private const val TAG = "RegisterActivity"
    }
}