package com.example.sengproject

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.sengproject.EncryptionUtils.encryptDataForFirebase
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt
import java.io.IOException
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class ManualDataCollectionActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var sensorEventListener: SensorEventListener

    private val speedReadings = mutableListOf<Float>()
    private val accelerationReadings = mutableListOf<Float>()
    private var lastLocation: Location? = null
    private var totalDistance = 0.0 // Distance in meters
    private var isDriveEnded = false

    private var averageSpeed: Double = 0.0
    private var averageAcceleration: Double = 0.0
    private var distanceTraveled: Double = 0.0

    private fun stopUpdates() {
        // Stop location updates when the activity is no longer active
        if (this::fusedLocationProviderClient.isInitialized && this::locationCallback.isInitialized) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }

        // Stop sensor updates
        if (this::sensorManager.isInitialized && this::sensorEventListener.isInitialized) {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    override fun onPause() {
        super.onPause()
        stopUpdates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_data_collection)

        // Initialize encryption key on first run
        if (isFirstRun()) {
            EncryptionUtils.generateSecretKey()
            markFirstRunComplete()
        }

        // Initialize FusedLocationProviderClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        setupLocationUpdates()
        setupAccelerationSensor()

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val isFirstTimeEndDrive = prefs.getBoolean("isFirstTimeEndDrive", true)
        val isFirstTimeCalculateScore = prefs.getBoolean("isFirstTimeCalculateScore", true)

        val endDriveButton: Button = findViewById(R.id.EndDriveButton)
        endDriveButton.setOnClickListener {
            if (!isDriveEnded) {
                // First click - end the drive
                calculateAveragesAndDistance()
                stopUpdates()
                isDriveEnded = true // Flag that the drive has ended
                endDriveButton.text = "Calculate Score" // Change button text to prompt for next action
                if (isFirstTimeCalculateScore) {
                    showCalculateScoreTooltip()
                    prefs.edit().putBoolean("isFirstTimeCalculateScore", false).apply()
                }
            } else {
                // Second click - save the drive and finish the activity
                saveDriveToDatabase(averageSpeed, averageAcceleration, distanceTraveled)
                finish() // Close the current activity
            }
        }

        if (isFirstTimeEndDrive) {
            showEndDriveTooltip()
            prefs.edit().putBoolean("isFirstTimeEndDrive", false).apply()
        }
    }

    private fun showEndDriveTooltip() {
        MaterialTapTargetPrompt.Builder(this)
            .setTarget(R.id.EndDriveButton)
            .setPrimaryText("End your drive")
            .setSecondaryText("Tap here to stop recording your drive and view your analytics.")
            .setBackButtonDismissEnabled(true)
            .setPromptStateChangeListener { prompt, state ->
                if (state == MaterialTapTargetPrompt.STATE_DISMISSING) {
                    // User has dismissed the prompt, perform any action here if needed
                }
            }
            .show()
    }

    private fun showCalculateScoreTooltip() {
        MaterialTapTargetPrompt.Builder(this)
            .setTarget(R.id.EndDriveButton) // Replace with the actual ID of your "Calculate Score" button
            .setPrimaryText("Calculate your score")
            .setSecondaryText("Tap here to save, calculate and see your driving score based on the data collected.")
            .setBackButtonDismissEnabled(true)
            .show()
    }


    private fun saveDriveToDatabase(averageSpeed: Double, averageAcceleration: Double, distanceTraveled: Double) {
        // Check if the distance traveled is greater than or equal to 1 kilometer
        if (distanceTraveled >= 1.0) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            userId?.let { uid ->
                // Encrypt the data before saving
                val encryptedAverageSpeed = encryptDataForFirebase(averageSpeed.toString())
                val encryptedAverageAcceleration = encryptDataForFirebase(averageAcceleration.toString())
                val encryptedDistanceTraveled = encryptDataForFirebase(distanceTraveled.toString())

                val driveRecord = hashMapOf(
                    "averageSpeed" to encryptedAverageSpeed,
                    "averageAcceleration" to encryptedAverageAcceleration,
                    "distanceTraveled" to encryptedDistanceTraveled
                )

                // Get a reference to the database and the user's drives node
                val databaseReference = FirebaseDatabase.getInstance().getReference("users/$uid/drives")

                // Create a new unique key for this drive
                val newDriveKey = databaseReference.push().key

                // Save the drive data under the new key
                newDriveKey?.let {
                    databaseReference.child(it).setValue(driveRecord)
                        .addOnSuccessListener {
                            // This block is executed if the data is saved successfully
                            Toast.makeText(this, "Drive data saved successfully!", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            // This block is executed if there is an error saving the data
                            Toast.makeText(this, "Failed to save drive data: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            } ?: run {
                // This block is executed if the user is not logged in
                Toast.makeText(this, "User must be logged in to save drive data", Toast.LENGTH_LONG).show()
            }
        } else {
            // This block is executed if the distance is less than 1km
            Toast.makeText(this, "Drive data not saved, distance traveled is less than 1 km.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionsAndStartLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission was granted, resume location updates
                checkPermissionsAndStartLocationUpdates()
            } else {
                // Permission denied, show an explanation to the user
                Toast.makeText(this, "Location permission is required for this feature", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        if (this::sensorEventListener.isInitialized) {
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Instead of duplicating the permission check code, call a method that handles it
        checkPermissionsAndStartLocationUpdates()
    }


    private fun setupLocationUpdates() {
        // Define how often you want updates
        locationRequest = LocationRequest.create().apply {
            interval = 1000 // Update interval in milliseconds
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    // Convert speed from m/s to km/h before adding to the list
                    val speedInKmh = location.speed * 3.6
                    speedReadings.add(speedInKmh.toFloat())

                    // Update UI with the current speed
                    val speedTextView: TextView = findViewById(R.id.SpeedTextView2)
                    speedTextView.text = "${String.format("%.2f", speedInKmh)} km/h"

                    // Perform reverse geocoding to get the street address
                    val geocoder = Geocoder(this@ManualDataCollectionActivity, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses != null) {
                            if (addresses.isNotEmpty()) {
                                val address = addresses[0]
                                val streetName = address.getAddressLine(0) // Get the street name from the address
                                val locationTextView: TextView = findViewById(R.id.LocationTextView2)
                                locationTextView.text = "$streetName"
                            }
                        }
                    } catch (e: IOException) {
                        // Handle exception (e.g., network or I/O issues)
                        Toast.makeText(this@ManualDataCollectionActivity, "Unable to get street name", Toast.LENGTH_SHORT).show()
                    }

                    val newLocation = locationResult.lastLocation
                    newLocation?.let { nonNullNewLocation ->
                        lastLocation?.let { nonNullLastLocation ->
                            totalDistance += nonNullLastLocation.distanceTo(nonNullNewLocation)
                        }
                        lastLocation = nonNullNewLocation
                        speedReadings.add(nonNullNewLocation.speed)
                    }
                }
            }
        }

        // Check for location permissions here (omitted for brevity) before requesting updates
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            checkPermissionsAndStartLocationUpdates()
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun setupAccelerationSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val acceleration = sqrt(event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)) - SensorManager.GRAVITY_EARTH
                    val accelerationTextView: TextView = findViewById(R.id.AccelerationTextView2)
                    accelerationTextView.text = "${String.format("%.2f", acceleration)} m/s²"
                    accelerationReadings.add(acceleration)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        // Register the listener for the accelerometer sensor
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun calculateAveragesAndDistance() {
        // Calculate the averages
        averageSpeed = if (speedReadings.isNotEmpty()) speedReadings.average() else 0.0
        averageAcceleration = if (accelerationReadings.isNotEmpty()) accelerationReadings.average() else 0.0
        distanceTraveled = totalDistance / 1000 // Convert meters to kilometers

        // Update UI with the calculated averages
        val speedTextView: TextView = findViewById(R.id.SpeedTextView1)
        val accelerationTextView: TextView = findViewById(R.id.AccelerationTextView1)
        val locationTextView: TextView = findViewById(R.id.LocationTextView1)
        val speedTextView2: TextView = findViewById(R.id.SpeedTextView2)
        val accelerationTextView2: TextView = findViewById(R.id.AccelerationTextView2)
        val locationTextView2: TextView = findViewById(R.id.LocationTextView2)

        speedTextView.text = "Average Speed:"
        accelerationTextView.text = "Average Acceleration:"
        locationTextView.text = "Distance Traveled:"
        speedTextView2.text = "${String.format("%.2f", averageSpeed)} km/h"
        accelerationTextView2.text = "${String.format("%.2f", averageAcceleration)} m/s²"
        locationTextView2.text = "${String.format("%.2f", distanceTraveled)} km"

        // Clear data
        speedReadings.clear()
        accelerationReadings.clear()
        totalDistance = 0.0
        lastLocation = null
    }

    private fun isFirstRun(): Boolean {
        // Get shared preferences
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        // Check if "isFirstRun" flag is true or not set (defaults to true)
        return sharedPreferences.getBoolean("isFirstRun", true)
    }

    private fun markFirstRunComplete() {
        // Get shared preferences editor
        val editor = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).edit()
        // Set "isFirstRun" flag to false
        editor.putBoolean("isFirstRun", false)
        // Apply changes to the shared preferences
        editor.apply()
    }
}
