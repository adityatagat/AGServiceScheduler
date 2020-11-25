package com.example.agservicescheduler

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
class DriverLoginActivity : AppCompatActivity() {

    private lateinit var mEmail: EditText
    private lateinit var mPassword: EditText

    private lateinit var mLogin: Button
    private lateinit var mRegister: Button

    private lateinit var mAuth: FirebaseAuth
    private lateinit var fbAuthListener: FirebaseAuth.AuthStateListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_login)

        //Get Firebase Auth state of login
        mAuth = FirebaseAuth.getInstance()

        fbAuthListener = FirebaseAuth.AuthStateListener() {
            val currentUser = mAuth.currentUser
            //Update UI accordingly
        }
        mEmail = findViewById(R.id.email)
        mPassword = findViewById(R.id.password)
        mLogin = findViewById(R.id.login_button)
        mRegister = findViewById(R.id.register_button)

        mRegister.setOnClickListener {
            val email = mEmail.text.toString()
            val password = mPassword.text.toString()
            mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "createUserWithEmail:success")
                        val userId = mAuth.currentUser?.uid
                        // Write a message to the database
                        val database = Firebase.database
                        userId?.let { it1 ->
                                database.reference.child("Users").child("Drivers").child(it1).child("name")
                            }?.setValue(email)

                        // Show a success message to the user after successful registration with Firebase
                        Snackbar.make(
                            mRegister, "Registered successfully!",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(Companion.TAG, "createUserWithEmail:failure", task.exception)
                        makeText(
                            baseContext, "Authentication failed.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        mLogin.setOnClickListener {
            val email = mEmail.text.toString()
            val password = mPassword.text.toString()
            mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithEmail:success")
                        val user = mAuth.currentUser
                        if (user != null) {
                            val intent = Intent(this, DriverMapsActivity::class.java)
                            startActivity(intent)
                        }
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(TAG, "signInWithEmail:failure", task.exception)
                        makeText(baseContext, "Authentication failed.",
                            Toast.LENGTH_SHORT).show()

                    }
                }
        }
    }

    override fun onStart() {
        super.onStart()
        mAuth.addAuthStateListener(fbAuthListener)
    }

    override fun onStop() {
        super.onStop()
        mAuth.removeAuthStateListener(fbAuthListener)
    }

    companion object {
        private const val TAG = "DriverLoginActivity"
    }
}
