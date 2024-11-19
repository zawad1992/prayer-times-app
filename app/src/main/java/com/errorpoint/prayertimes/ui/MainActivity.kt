package com.errorpoint.prayertimes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.batoulapps.adhan2.*
import com.batoulapps.adhan2.data.DateComponents
import com.errorpoint.prayertimes.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.datetime.toInstant

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var spinnerMadhab: Spinner
    private lateinit var spinnerIshaMethod: Spinner
    private lateinit var spinnerCalculation: Spinner
    private lateinit var datePicker: DatePicker
    private lateinit var prayerTimesContainer: LinearLayout
    private lateinit var locationText: TextView
    private var userLocation: Coordinates? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1234
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupSpinners()
        setupLocationServices()
    }

    private fun initializeViews() {
        spinnerMadhab = findViewById(R.id.spinnerMadhab)
        spinnerIshaMethod = findViewById(R.id.spinnerIshaMethod)
        spinnerCalculation = findViewById(R.id.spinnerCalculation)
        datePicker = findViewById(R.id.datePicker)
        prayerTimesContainer = findViewById(R.id.prayerTimesContainer)
        locationText = findViewById(R.id.locationText)

        findViewById<Button>(R.id.btnGetLocation).setOnClickListener {
            checkLocationPermissionAndGetLocation()
        }
    }

    private fun setupSpinners() {
        // Madhab Spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.madhab_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMadhab.adapter = adapter
        }

        // Isha Method Spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.isha_method_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerIshaMethod.adapter = adapter
        }

        // Calculation Method Spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.calculation_method_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCalculation.adapter = adapter
        }

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculatePrayerTimes()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerMadhab.onItemSelectedListener = listener
        spinnerIshaMethod.onItemSelectedListener = listener
        spinnerCalculation.onItemSelectedListener = listener
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun checkLocationPermissionAndGetLocation() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getLocation()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    userLocation = Coordinates(it.latitude, it.longitude)
                    locationText.text = "Location: ${it.latitude.format(4)}°, ${it.longitude.format(4)}°"
                    calculatePrayerTimes()
                }
            }
        }
    }

    private fun calculatePrayerTimes() {
        userLocation?.let { coordinates ->
            try {
                // Create DateComponents for the selected date
                val dateComponents = DateComponents(
                    datePicker.year,
                    datePicker.month + 1,
                    datePicker.dayOfMonth
                )

                // Get the base calculation method parameters
                val baseParams = when (spinnerCalculation.selectedItemPosition) {
                    0 -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
                    1 -> CalculationMethod.NORTH_AMERICA.parameters
                    2 -> CalculationMethod.EGYPTIAN.parameters
                    3 -> CalculationMethod.KARACHI.parameters
                    else -> CalculationMethod.DUBAI.parameters
                }

                // Create new parameters with the selected madhab
                val params = baseParams.copy(
                    madhab = if (spinnerMadhab.selectedItemPosition == 0) {
                        Madhab.SHAFI
                    } else {
                        Madhab.HANAFI
                    }
                )

                // Calculate prayer times
                val prayerTimes = PrayerTimes(coordinates, dateComponents, params)

                // Get Sunnah times for night calculations
                val sunnahTimes = SunnahTimes(prayerTimes)

                // Calculate Isha end time based on selection
                val ishaEndTime = when (spinnerIshaMethod.selectedItemPosition) {
                    0 -> sunnahTimes.lastThirdOfTheNight
                    1 -> sunnahTimes.middleOfTheNight
                    else -> prayerTimes.fajr // Next day's Fajr
                }

                displayPrayerTimes(prayerTimes, ishaEndTime)
            } catch (e: Exception) {
                Toast.makeText(this, "Error calculating prayer times: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayPrayerTimes(prayerTimes: PrayerTimes, ishaEndTime: kotlinx.datetime.Instant) {
        prayerTimesContainer.removeAllViews()

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val prayers = listOf(
            "Fajr" to Pair(prayerTimes.fajr, prayerTimes.sunrise),
            "Sunrise" to Pair(prayerTimes.sunrise, prayerTimes.dhuhr),
            "Dhuhr" to Pair(prayerTimes.dhuhr, prayerTimes.asr),
            "Asr" to Pair(prayerTimes.asr, prayerTimes.maghrib),
            "Maghrib" to Pair(prayerTimes.maghrib, prayerTimes.isha),
            "Isha" to Pair(prayerTimes.isha, ishaEndTime)
        )

        val currentTime = kotlinx.datetime.Clock.System.now()

        prayers.forEach { (name, times) ->
            val prayerView = layoutInflater.inflate(R.layout.prayer_time_item, prayerTimesContainer, false)

            prayerView.findViewById<TextView>(R.id.prayerName).text = name
            prayerView.findViewById<TextView>(R.id.startTime).text =
                "Start: ${timeFormat.format(Date(times.first.toEpochMilliseconds()))}"
            prayerView.findViewById<TextView>(R.id.endTime).text =
                "End: ${timeFormat.format(Date(times.second.toEpochMilliseconds()))}"

            if (currentTime >= times.first && currentTime <= times.second) {
                prayerView.setBackgroundResource(R.color.currentPrayer)
            }

            prayerTimesContainer.addView(prayerView)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation()
                } else {
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}