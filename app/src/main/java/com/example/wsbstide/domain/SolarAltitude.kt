import kotlin.math.*

// Ported from Legacy_WSBSTide/WXTSrc47/LOCLIB.C (skycal.cc section).
// Same algorithm, same constants, so sun events match the legacy exactly.

private const val DEG_IN_RADIAN = 57.29577951308232  // 180/π
private const val HRS_IN_RADIAN = 3.81971863420549   // 12/π
private const val SUNRISE_ALTITUDE = -0.83            // degrees (sea level)

// Low-precision sun RA (hours) and Dec (degrees). From legacy lpsun().
private fun lpsun(jd: Double): Pair<Double, Double> {
    val n = jd - 2451545.0
    val L = 280.460 + 0.9856474 * n
    val gRad = Math.toRadians(357.528 + 0.9856003 * n)
    val lambdaRad = Math.toRadians(L + 1.915 * sin(gRad) + 0.020 * sin(2.0 * gRad))
    val epsilonRad = Math.toRadians(23.439 - 0.0000004 * n)

    val x = cos(lambdaRad)
    val y = cos(epsilonRad) * sin(lambdaRad)
    val z = sin(epsilonRad) * sin(lambdaRad)

    var raRad = atan2(y, x)
    if (raRad < 0.0) raRad += 2.0 * PI
    return Pair(raRad * HRS_IN_RADIAN, asin(z) * DEG_IN_RADIAN)
}

// Local Mean Sidereal Time in hours. From legacy lst().
// lonHrsWest: longitude in HOURS WEST (positive west of Greenwich).
private fun lst(jd: Double, lonHrsWest: Double): Double {
    val jdInt = floor(jd).toLong().toDouble()
    val jdFrac = jd - jdInt
    val jdMid: Double
    val ut: Double
    if (jdFrac < 0.5) { jdMid = jdInt - 0.5; ut = jdFrac + 0.5 }
    else               { jdMid = jdInt + 0.5; ut = jdFrac - 0.5 }

    val t = (jdMid - 2451545.0) / 36525.0
    var sidG = (24110.54841 + 8640184.812866 * t + 0.093104 * t * t - 6.2e-6 * t * t * t) / 86400.0
    sidG -= floor(sidG)
    sidG = sidG + 1.0027379093 * ut - lonHrsWest / 24.0
    sidG -= floor(sidG)
    val result = sidG * 24.0
    return if (result < 0.0) result + 24.0 else result
}

// Solar altitude in degrees for a given Unix timestamp and location.
fun sunAltitudeDeg(unixSec: Double, latDeg: Double, lonEastDeg: Double): Double {
    val jd = unixSec / 86400.0 + 2440587.5
    val (raHrs, decDeg) = lpsun(jd)

    // skycal uses hours WEST; we have degrees EAST → convert
    val haHrs = lst(jd, -lonEastDeg / 15.0) - raHrs
    val decRad = Math.toRadians(decDeg)
    val haRad  = haHrs / HRS_IN_RADIAN
    val latRad = Math.toRadians(latDeg)

    return DEG_IN_RADIAN * asin(cos(decRad) * cos(haRad) * cos(latRad) + sin(decRad) * sin(latRad))
}

/**
 * Returns (startsAsDay, sortedTransitionMillis).
 * Scans [startMs, endMs] in 5-minute steps and bisects each sun altitude
 * zero-crossing to ~1-second precision.
 */
fun sunEvents(latDeg: Double, lonEastDeg: Double, startMs: Long, endMs: Long): Pair<Boolean, List<Long>> {
    val stepMs = 5L * 60_000L
    val events = mutableListOf<Long>()

    var t = startMs
    var prevUp = sunAltitudeDeg(t / 1000.0, latDeg, lonEastDeg) > SUNRISE_ALTITUDE
    val startsAsDay = prevUp

    while (t < endMs) {
        val nextT = (t + stepMs).coerceAtMost(endMs)
        val nextUp = sunAltitudeDeg(nextT / 1000.0, latDeg, lonEastDeg) > SUNRISE_ALTITUDE

        if (prevUp != nextUp) {
            var lo = t; var hi = nextT
            repeat(20) {
                val mid = (lo + hi) / 2
                if ((sunAltitudeDeg(mid / 1000.0, latDeg, lonEastDeg) > SUNRISE_ALTITUDE) == prevUp) lo = mid
                else hi = mid
            }
            events += (lo + hi) / 2
        }

        prevUp = nextUp
        t = nextT
    }
    return Pair(startsAsDay, events)
}
