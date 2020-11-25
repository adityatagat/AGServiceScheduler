package com.example.agservicescheduler

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import java.util.*
import kotlin.collections.HashMap

class CustomerSettingsActivity: AppCompatActivity() {
    private lateinit var mConfirm: Button
    private lateinit var mBack: Button
    private lateinit var mNameField: EditText
    private lateinit var mPhoneField: EditText
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mCustomerDatabaseReference: DatabaseReference
    private var userId: String? = null

    private lateinit var mName: String
    private lateinit var mPhone: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_settings)

        mConfirm = findViewById(R.id.confirm_button)
        mBack = findViewById(R.id.back_button)
        mNameField = findViewById(R.id.customer_name)
        mPhoneField = findViewById(R.id.customer_phone)

        mAuth = FirebaseAuth.getInstance()
        userId = mAuth.currentUser?.uid
        mCustomerDatabaseReference = FirebaseDatabase.getInstance().reference.child("Users").child("Customers").child((userId.toString()))
        getUserInfo()
        mConfirm.setOnClickListener { saveUserInformation() }
        mBack.setOnClickListener { finish() }
    }
    private fun getUserInfo() {
        mCustomerDatabaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists() && snapshot.hasChildren()) {
                    val map = snapshot.getValue<Map<String, Any>>()
                    if (map != null) {
                        if(map["name"] !=null) {
                            mName = map["name"].toString()
                            mNameField.setText(mName)
                        }
                        if(map["phone"] !=null) {
                            mPhone = map["phone"].toString()
                            mPhoneField.setText(mPhone)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value", error.toException())
            }

        })
    }
    private fun saveUserInformation() {

        mName = mNameField.text.toString()
        mPhone = mPhoneField.text.toString()

        val userInfo = HashMap<String, Any>()
        userInfo["name"] = mName
        userInfo["phone"] = mPhone

        mCustomerDatabaseReference.updateChildren(userInfo as Map<String, Any>)
        Toast.makeText(baseContext, "User Info saved successfully!", Toast.LENGTH_LONG).show()

    }

    companion object {
        private const val TAG = "CustomerSettingsActivit"
    }
}
