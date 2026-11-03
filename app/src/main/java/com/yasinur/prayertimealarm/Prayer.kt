package com.yasinur.prayertimealarm

import android.content.Context
import android.net.Uri
import java.util.Locale

enum class Prayer(val id: Int, val banglaName: String, val defaultHour: Int, val defaultMinute: Int) {
    FAJR(0, "ফজর", 5, 0),
    DHUHR(1, "যোহর", 12, 15),
    ASR(2, "আসর", 15, 45),
    MAGHRIB(3, "মাগরিব", 17, 30),
    ISHA(4, "এশা", 19, 15);

    companion object {
        fun fromName(name: String?): Prayer? = entries.firstOrNull { it.name == name }
    }
}

/** Stores each prayer's custom time, on/off state and the chosen azan audio. */
object PrayerPrefs {
    private const val NAME = "prayer_prefs"
    private const val KEY_AUDIO = "azan_audio_uri"

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun hour(c: Context, p: Prayer) = sp(c).getInt("h_${p.name}", p.defaultHour)
    fun minute(c: Context, p: Prayer) = sp(c).getInt("m_${p.name}", p.defaultMinute)
    fun isEnabled(c: Context, p: Prayer) = sp(c).getBoolean("e_${p.name}", true)

    fun setTime(c: Context, p: Prayer, hour: Int, minute: Int) {
        sp(c).edit().putInt("h_${p.name}", hour).putInt("m_${p.name}", minute).apply()
    }

    fun setEnabled(c: Context, p: Prayer, enabled: Boolean) {
        sp(c).edit().putBoolean("e_${p.name}", enabled).apply()
    }

    fun azanUri(c: Context): Uri? = sp(c).getString(KEY_AUDIO, null)?.let(Uri::parse)

    fun setAzanUri(c: Context, uri: Uri?) {
        sp(c).edit().putString(KEY_AUDIO, uri?.toString()).apply()
    }
}

fun formatTime(hour: Int, minute: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    val period = when (hour) {
        in 4..11 -> "সকাল"
        in 12..15 -> "দুপুর"
        in 16..17 -> "বিকাল"
        in 18..19 -> "সন্ধ্যা"
        else -> "রাত"
    }
    val digits = String.format(Locale.US, "%d:%02d", h12, minute)
    return "$period ${toBanglaDigits(digits)}"
}

private fun toBanglaDigits(s: String): String =
    s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")
