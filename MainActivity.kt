package com.example.togglemaster

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: LinearLayout
    private lateinit var toggleCircle: View
    private lateinit var statusText: TextView
    private lateinit var gestureArea: View
    private lateinit var cameraManager: CameraManager
    private var isOn = false
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        toggleButton = findViewById(R.id.toggleButton)
        toggleCircle = findViewById(R.id.toggleCircle)
        statusText = findViewById(R.id.statusText)
        gestureArea = findViewById(R.id.gestureArea)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Load saved state
        authenticateUser()

        // Toggle button touch listener
        toggleButton.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                toggleState()
                updateWidget()
                true
            } else false
        }

        // Gesture detector for swipe controls
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 != null && e2 != null) {
                    if (e2.x - e1.x > 50) { // Swipe right
                        if (!isOn) toggleState()
                    } else if (e1.x - e2.x > 50) { // Swipe left
                        if (isOn) toggleState()
                    }
                }
                return true
            }
        })

        gestureArea.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun toggleState() {
        isOn = !isOn
        updateUI()
        toggleFlashlight()
        saveToggleState()
        checkBatteryLevel()
    }

    private fun updateUI() {
        val slideAnimation = if (isOn) {
            toggleButton.setBackgroundColor(resources.getColor(R.color.green))
            AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        } else {
            toggleButton.setBackgroundColor(resources.getColor(R.color.gray))
            AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
        }

        toggleCircle.startAnimation(slideAnimation.apply {
            setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    toggleCircle.translationX = if (isOn) 60f else 0f
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
        })

        statusText.text = if (isOn) "ON" else "OFF"
    }

    private fun toggleFlashlight() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, isOn)
        } catch (e: Exception) {
            Toast.makeText(this, "Flashlight error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkBatteryLevel() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryLevel < 20 && isOn) {
            Toast.makeText(this, "Low battery! Consider turning off.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToggleState() {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "TogglePrefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        with(sharedPreferences.edit()) {
            putBoolean("toggle_state", isOn)
            apply()
        }
    }

    private fun loadToggleState() {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "TogglePrefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        isOn = sharedPreferences.getBoolean("toggle_state", false)
        updateUI()
        toggleFlashlight()
    }

    private fun authenticateUser() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                loadToggleState()
                Toast.makeText(this@MainActivity, "Authentication succeeded!", Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ToggleMaster Security")
            .setSubtitle("Authenticate to access")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun updateWidget() {
        val intent = Intent(this, ToggleWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            ComponentName(application, ToggleWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }
}
