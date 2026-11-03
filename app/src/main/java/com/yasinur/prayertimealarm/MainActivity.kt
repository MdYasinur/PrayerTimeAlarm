package com.yasinur.prayertimealarm

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prayerList: LinearLayout
    private lateinit var audioLabel: TextView

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            PrayerPrefs.setAzanUri(this, uri)
            updateAudioLabel()
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prayerList = findViewById(R.id.prayerList)
        audioLabel = findViewById(R.id.audioLabel)

        findViewById<MaterialButton>(R.id.btnPickAudio).setOnClickListener {
            pickAudio.launch(arrayOf("audio/*"))
        }
        findViewById<MaterialButton>(R.id.btnResetAudio).setOnClickListener {
            PrayerPrefs.setAzanUri(this, null)
            updateAudioLabel()
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener {
            AzanService.start(this, null)
        }
        findViewById<MaterialButton>(R.id.btnStop).setOnClickListener {
            AzanService.stop(this)
        }

        buildPrayerRows()
        updateAudioLabel()
        requestPermissions()
        AlarmScheduler.scheduleAll(this)
    }

    override fun onResume() {
        super.onResume()
        // The exact-alarm permission may have just been granted in Settings.
        AlarmScheduler.scheduleAll(this)
    }

    private fun buildPrayerRows() {
        prayerList.removeAllViews()
        for (prayer in Prayer.entries) {
            val row = layoutInflater.inflate(R.layout.item_prayer, prayerList, false)
            val name = row.findViewById<TextView>(R.id.prayerName)
            val time = row.findViewById<TextView>(R.id.prayerTime)
            val toggle = row.findViewById<SwitchMaterial>(R.id.prayerSwitch)

            name.text = prayer.banglaName
            time.text = formatTime(PrayerPrefs.hour(this, prayer), PrayerPrefs.minute(this, prayer))
            toggle.isChecked = PrayerPrefs.isEnabled(this, prayer)
            row.alpha = if (toggle.isChecked) 1f else 0.55f

            toggle.setOnCheckedChangeListener { _, checked ->
                PrayerPrefs.setEnabled(this, prayer, checked)
                AlarmScheduler.schedule(this, prayer)
                row.alpha = if (checked) 1f else 0.55f
            }

            row.findViewById<MaterialCardView>(R.id.card).setOnClickListener {
                TimePickerDialog(
                    this,
                    { _, h, m ->
                        PrayerPrefs.setTime(this, prayer, h, m)
                        AlarmScheduler.schedule(this, prayer)
                        time.text = formatTime(h, m)
                        Toast.makeText(this, "${prayer.banglaName}: ${formatTime(h, m)}", Toast.LENGTH_SHORT).show()
                    },
                    PrayerPrefs.hour(this, prayer),
                    PrayerPrefs.minute(this, prayer),
                    DateFormat.is24HourFormat(this)
                ).show()
            }

            prayerList.addView(row)
        }
    }

    private fun updateAudioLabel() {
        val uri = PrayerPrefs.azanUri(this)
        audioLabel.text = if (uri == null) getString(R.string.default_sound) else displayName(uri)
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.exact_alarm_title)
                    .setMessage(R.string.exact_alarm_msg)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                        )
                    }
                    .setNegativeButton(R.string.later, null)
                    .show()
            }
        }
    }
}
