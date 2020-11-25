package com.example.agservicescheduler

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import java.util.*
import kotlin.collections.HashMap

class DriverSettingsActivity: AppCompatActivity() {
    private lateinit var mConfirm: Button
    private lateinit var mBack: Button
    private lateinit var mNameField: EditText
    private lateinit var mPhoneField: EditText
    //private lateinit var mServiceTypeField: EditText
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDriverDatabaseReference: DatabaseReference
    private var userId: String? = null

    private lateinit var mName: String
    private lateinit var mPhone: String
    private lateinit var mServiceType: String

    private lateinit var mRadioGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_settings)

        mConfirm = findViewById(R.id.confirm_button)
        mBack = findViewById(R.id.back_button)
        mNameField = findViewById(R.id.driver_name)
        mPhoneField = findViewById(R.id.driver_phone)
        //mServiceTypeField = findViewById(R.id.driver_type)
        mAuth = FirebaseAuth.getInstance()
        userId = mAuth.currentUser?.uid
        mDriverDatabaseReference = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child((userId.toString()))
        getUserInfo()
        mConfirm.setOnClickListener { saveUserInformation() }
        mBack.setOnClickListener { finish() }

        mRadioGroup = findViewById(R.id.radioGroup)

    }
    private fun getUserInfo() {
        mDriverDatabaseReference.addValueEventListener(object : ValueEventListener {
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
                        if(map["serviceType"] !=null) {
                            mServiceType = map["serviceType"].toString()
                            when(mServiceType) {
                                "Bulk" -> mRadioGroup.check(R.id.bulk)
                                "Cylinder" -> mRadioGroup.check(R.id.cylinder)
                                "Service" -> mRadioGroup.check(R.id.service)
                                else -> mRadioGroup.clearCheck()
                            }
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
//        mServiceType = mServiceTypeField.text.toString()

        val selectedRadioButtonId = mRadioGroup.checkedRadioButtonId
        val radioButton: RadioButton = findViewById(selectedRadioButtonId)

        val userInfo = HashMap<String, Any>()
        userInfo["name"] = mName
        userInfo["phone"] = mPhone
        userInfo["serviceType"] = radioButton.text.toString()

        mDriverDatabaseReference.updateChildren(userInfo as Map<String, Any>)
        Toast.makeText(baseContext, "User Info saved successfully!", Toast.LENGTH_LONG).show()

    }

    companion object {
        private const val TAG = "DriverSettingsActivity"
    }
}
