package sk.slnko.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMS = 42
        private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
        private val DATE_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale("sk"))

        /** Slovenský tvar slova „minúta“ po predložke „o“ / „pred“. */
        fun minutesWord(n: Long): String = when {
            n == 1L -> "minútu"
            n in 2L..4L -> "minúty"
            else -> "minút"
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var tvPlace: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvSunrise: TextView
    private lateinit var tvSunset: TextView
    private lateinit var tvDayLength: TextView
    private lateinit var tvTomorrow: TextView
    private lateinit var tvNext: TextView
    private lateinit var swRise: Switch
    private lateinit var swSet: Switch
    private lateinit var npRise: NumberPicker
    private lateinit var npSet: NumberPicker

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Notifications.ensureChannel(this)

        tvPlace = findViewById(R.id.tvPlace)
        tvDate = findViewById(R.id.tvDate)
        tvSunrise = findViewById(R.id.tvSunrise)
        tvSunset = findViewById(R.id.tvSunset)
        tvDayLength = findViewById(R.id.tvDayLength)
        tvTomorrow = findViewById(R.id.tvTomorrow)
        tvNext = findViewById(R.id.tvNext)
        swRise = findViewById(R.id.swRise)
        swSet = findViewById(R.id.swSet)
        npRise = findViewById(R.id.npRise)
        npSet = findViewById(R.id.npSet)

        setupPicker(npRise, prefs.minutesBeforeSunrise) { prefs.minutesBeforeSunrise = it }
        setupPicker(npSet, prefs.minutesBeforeSunset) { prefs.minutesBeforeSunset = it }

        swRise.isChecked = prefs.notifySunrise
        swSet.isChecked = prefs.notifySunset
        swRise.setOnCheckedChangeListener { _, checked ->
            prefs.notifySunrise = checked
            onSettingsChanged(checked)
        }
        swSet.setOnCheckedChangeListener { _, checked ->
            prefs.notifySunset = checked
            onSettingsChanged(checked)
        }

        findViewById<Button>(R.id.btnLocation).setOnClickListener {
            if (hasLocationPermission()) refreshLocation(showToast = true) else askPermissions()
        }
        findViewById<Button>(R.id.btnPlayBell).setOnClickListener {
            Notifications.playBell(this)
        }
        findViewById<Button>(R.id.btnSound).setOnClickListener {
            Notifications.openSoundSettings(this)
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            askNotificationPermissionIfNeeded()
            Notifications.show(this, SunEvent.SUNRISE, System.currentTimeMillis() + 10 * 60_000L)
        }

        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) refreshLocation(showToast = false)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun setupPicker(np: NumberPicker, value: Int, save: (Int) -> Unit) {
        np.minValue = 0
        np.maxValue = 180
        np.value = value.coerceIn(0, 180)
        np.wrapSelectorWheel = false
        np.setOnValueChangedListener { _, _, newVal ->
            save(newVal)
            Scheduler.scheduleAll(this)
            renderNext()
        }
    }

    private fun onSettingsChanged(turnedOn: Boolean) {
        if (turnedOn) askNotificationPermissionIfNeeded()
        Scheduler.scheduleAll(this)
        renderNext()
    }

    // ---------- Oprávnenia ----------

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val needed = mutableListOf<String>()
        if (!hasLocationPermission()) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQ_PERMS)
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS && hasLocationPermission()) refreshLocation(showToast = false)
        render()
    }

    // ---------- Poloha ----------

    private fun refreshLocation(showToast: Boolean) {
        val lm = getSystemService(LocationManager::class.java) ?: return
        try {
            // 1) Okamžite použijeme poslednú známu polohu
            val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null) applyLocation(last)

            // 2) A vyžiadame si aktuálnu
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider == null) {
                if (showToast || !prefs.hasLocation) {
                    Toast.makeText(this, R.string.location_off, Toast.LENGTH_LONG).show()
                }
                return
            }
            if (showToast) Toast.makeText(this, R.string.location_searching, Toast.LENGTH_SHORT).show()

            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, mainExecutor) { loc ->
                    if (loc != null) applyLocation(loc)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        applyLocation(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            askPermissions()
        }
    }

    private fun applyLocation(loc: Location) {
        prefs.saveLocation(loc.latitude, loc.longitude)
        Scheduler.scheduleAll(this)
        render()
        lookupPlaceName(loc.latitude, loc.longitude)
    }

    private fun lookupPlaceName(lat: Double, lon: Double) {
        if (!Geocoder.isPresent()) return
        Thread {
            try {
                @Suppress("DEPRECATION")
                val list = Geocoder(this, Locale("sk")).getFromLocation(lat, lon, 1)
                val a = list?.firstOrNull()
                val name = a?.locality ?: a?.subAdminArea ?: a?.adminArea
                if (name != null) {
                    prefs.placeName = name
                    runOnUiThread { render() }
                }
            } catch (_: Exception) {
                // Bez internetu názov miesta nezistíme – nevadí.
            }
        }.start()
    }

    // ---------- Zobrazenie ----------

    private fun coordText(): String {
        val lat = prefs.latitude
        val lon = prefs.longitude
        val ns = if (lat >= 0) "s. š." else "j. š."
        val ew = if (lon >= 0) "v. d." else "z. d."
        return String.format(Locale("sk"), "%.3f° %s, %.3f° %s", abs(lat), ns, abs(lon), ew)
    }

    private fun render() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        tvDate.text = today.format(DATE_FMT).replaceFirstChar { it.uppercase() }

        if (!prefs.hasLocation) {
            tvPlace.setText(R.string.no_location)
            tvSunrise.text = "–"
            tvSunset.text = "–"
            tvDayLength.text = ""
            tvTomorrow.text = ""
            renderNext()
            return
        }

        val place = prefs.placeName
        tvPlace.text = if (place != null) "$place\n${coordText()}" else coordText()

        val t = SunCalc.compute(today, prefs.latitude, prefs.longitude, zone)
        when (t.type) {
            SunCalc.DayType.NORMAL -> {
                tvSunrise.text = t.sunrise!!.format(TIME_FMT)
                tvSunset.text = t.sunset!!.format(TIME_FMT)
                tvDayLength.text = getString(R.string.day_length, durationText(t.sunrise, t.sunset))
            }
            SunCalc.DayType.POLAR_DAY -> {
                tvSunrise.text = "–"; tvSunset.text = "–"
                tvDayLength.setText(R.string.polar_day)
            }
            SunCalc.DayType.POLAR_NIGHT -> {
                tvSunrise.text = "–"; tvSunset.text = "–"
                tvDayLength.setText(R.string.polar_night)
            }
        }

        val tm = SunCalc.compute(today.plusDays(1), prefs.latitude, prefs.longitude, zone)
        tvTomorrow.text = if (tm.sunrise != null && tm.sunset != null) {
            getString(R.string.tomorrow, tm.sunrise.format(TIME_FMT), tm.sunset.format(TIME_FMT))
        } else ""

        renderNext()
    }

    private fun durationText(a: ZonedDateTime, b: ZonedDateTime): String {
        val d = Duration.between(a, b)
        return "${d.toHours()} h ${d.toMinutes() % 60} min"
    }

    private fun renderNext() {
        val lines = mutableListOf<String>()
        if (prefs.notifySunrise) {
            Scheduler.findNext(this, SunEvent.SUNRISE)?.let {
                lines += getString(R.string.next_rise, whenText(it.triggerMillis))
            }
        }
        if (prefs.notifySunset) {
            Scheduler.findNext(this, SunEvent.SUNSET)?.let {
                lines += getString(R.string.next_set, whenText(it.triggerMillis))
            }
        }
        tvNext.text = when {
            lines.isNotEmpty() -> lines.joinToString("\n")
            prefs.notifySunrise || prefs.notifySunset -> getString(R.string.need_location)
            else -> getString(R.string.notifications_off)
        }
    }

    private fun whenText(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val dt = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (dt.toLocalDate()) {
            today -> "dnes"
            today.plusDays(1) -> "zajtra"
            else -> dt.format(DateTimeFormatter.ofPattern("d. M.", Locale("sk")))
        }
        return "$day o ${dt.format(TIME_FMT)}"
    }
}
