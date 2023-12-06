package com.example.sengproject

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt

class HubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)

        if (isFirstLaunch) {
            MaterialTapTargetPrompt.Builder(this@HubActivity)
                .setTarget(R.id.DriveButton)
                .setPrimaryText("Start your Drive")
                .setSecondaryText("Tap here to begin recording your drive data")
                .setPromptStateChangeListener { _, state ->
                    if (state == MaterialTapTargetPrompt.STATE_DISMISSED ||
                        state == MaterialTapTargetPrompt.STATE_FINISHED
                    ) {
                        // Update the flag so the prompt isn't shown again
                        prefs.edit().putBoolean("isFirstLaunch", false).apply()
                    }
                }
                .show()
        }

        val recordDriveButton: Button = findViewById(R.id.DriveButton)
        recordDriveButton.setOnClickListener {
            val intent = Intent(this, ManualDataCollectionActivity::class.java)
            startActivity(intent)
        }
    }
}