package sk.slnko.app

import android.content.Context
import android.content.SharedPreferences

/** Uložené nastavenia a posledná známa poloha. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("slnko", Context.MODE_PRIVATE)

    val hasLocation: Boolean get() = sp.contains("lat") && sp.contains("lon")

    val latitude: Double get() = java.lang.Double.longBitsToDouble(sp.getLong("lat", 0L))
    val longitude: Double get() = java.lang.Double.longBitsToDouble(sp.getLong("lon", 0L))

    fun saveLocation(lat: Double, lon: Double) {
        sp.edit()
            .putLong("lat", java.lang.Double.doubleToRawLongBits(lat))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(lon))
            .putLong("locTime", System.currentTimeMillis())
            .apply()
    }

    val locationTime: Long get() = sp.getLong("locTime", 0L)

    var placeName: String?
        get() = sp.getString("place", null)
        set(v) = sp.edit().putString("place", v).apply()

    var notifySunrise: Boolean
        get() = sp.getBoolean("notifyRise", false)
        set(v) = sp.edit().putBoolean("notifyRise", v).apply()

    var notifySunset: Boolean
        get() = sp.getBoolean("notifySet", false)
        set(v) = sp.edit().putBoolean("notifySet", v).apply()

    var minutesBeforeSunrise: Int
        get() = sp.getInt("minRise", 10)
        set(v) = sp.edit().putInt("minRise", v).apply()

    var minutesBeforeSunset: Int
        get() = sp.getInt("minSet", 10)
        set(v) = sp.edit().putInt("minSet", v).apply()
}
