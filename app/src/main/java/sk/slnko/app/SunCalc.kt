package sk.slnko.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Výpočet východu a západu slnka (rovnica východu slnka, presnosť cca ±1 minúta).
 * Funguje úplne offline – stačí zemepisná šírka a dĺžka.
 */
object SunCalc {

    enum class DayType { NORMAL, POLAR_DAY, POLAR_NIGHT }

    data class SunTimes(
        val sunrise: ZonedDateTime?,
        val sunset: ZonedDateTime?,
        val type: DayType
    )

    private const val RAD = PI / 180.0

    private fun normalize(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    private fun julianToZoned(j: Double, zone: ZoneId): ZonedDateTime {
        val millis = ((j - 2440587.5) * 86_400_000.0).roundToLong()
        return Instant.ofEpochMilli(millis).atZone(zone)
    }

    fun compute(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes {
        // Počet dní od J2000.0 (poludnie UTC daného dňa)
        val n = (date.toEpochDay() + 2440588.0 - 2451545.0 + 0.0008).roundToLong().toDouble()
        // Stredný slnečný čas (zemepisná dĺžka – východ kladný)
        val jStar = n - longitude / 360.0
        // Stredná anomália
        val m = normalize(357.5291 + 0.98560028 * jStar)
        val mr = m * RAD
        // Rovnica stredu
        val c = 1.9148 * sin(mr) + 0.0200 * sin(2 * mr) + 0.0003 * sin(3 * mr)
        // Ekliptikálna dĺžka
        val lambda = normalize(m + c + 180.0 + 102.9372)
        val lr = lambda * RAD
        // Slnečné poludnie
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(mr) - 0.0069 * sin(2 * lr)
        // Deklinácia slnka
        val sinDec = sin(lr) * sin(23.4397 * RAD)
        val cosDec = cos(asin(sinDec))
        // Hodinový uhol (-0.833° = refrakcia + polomer slnka)
        val latR = latitude * RAD
        val cosW = (sin(-0.833 * RAD) - sin(latR) * sinDec) / (cos(latR) * cosDec)

        if (cosW > 1.0) return SunTimes(null, null, DayType.POLAR_NIGHT)
        if (cosW < -1.0) return SunTimes(null, null, DayType.POLAR_DAY)

        val w = acos(cosW) / RAD
        val rise = julianToZoned(jTransit - w / 360.0, zone)
        val set = julianToZoned(jTransit + w / 360.0, zone)
        return SunTimes(rise, set, DayType.NORMAL)
    }
}
