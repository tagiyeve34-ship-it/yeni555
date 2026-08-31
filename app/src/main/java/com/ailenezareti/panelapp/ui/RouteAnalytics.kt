package com.ailenezareti.panelapp.ui

import com.ailenezareti.panelapp.model.LocationPoint
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.*

data class StopInfo(
    val index: Int,
    val start: LocationPoint,
    val end: LocationPoint,
    val centerLat: Double,
    val centerLon: Double,
    val durationMs: Long
)

data class RouteStats(
    val points: List<LocationPoint>,
    val segments: List<List<LocationPoint>>,
    val stops: List<StopInfo>,
    val distanceMeters: Double,
    val movingMs: Long,
    val stoppedMs: Long
)

object RouteAnalytics {
    private const val STOP_RADIUS_M = 100.0
    private const val STOP_MIN_MS = 5 * 60 * 1000L
    private const val MAX_ACCURACY_M = 150.0
    private const val MAX_SPEED_KMH = 180.0
    private const val SEGMENT_GAP_MS = 15 * 60 * 1000L

    fun analyze(raw: List<LocationPoint>): RouteStats {
        val chronological = raw.sortedBy { parseTime(it.recorded_at) }
        val clean = clean(chronological)
        val segments = splitSegments(clean)
        val stops = detectStops(clean)

        var distance = 0.0
        var movingMs = 0L
        for (segment in segments) {
            for (i in 1 until segment.size) {
                val a = segment[i - 1]
                val b = segment[i]
                val d = distance(a, b)
                val dt = (parseTime(b.recorded_at) - parseTime(a.recorded_at)).coerceAtLeast(0L)
                distance += d
                if (d >= 25.0) movingMs += dt
            }
        }
        val stoppedMs = stops.sumOf { it.durationMs }
        return RouteStats(clean, segments, stops, distance, movingMs, stoppedMs)
    }

    private fun clean(src: List<LocationPoint>): List<LocationPoint> {
        val valid = src.filter { p ->
            val lat = p.latitude.toDoubleOrNull()
            val lon = p.longitude.toDoubleOrNull()
            val acc = p.accuracy_m?.toDoubleOrNull()
            lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0 && (acc == null || acc <= MAX_ACCURACY_M)
        }
        if (valid.size < 3) return valid

        val deSpiked = mutableListOf<LocationPoint>()
        for (i in valid.indices) {
            if (i == 0 || i == valid.lastIndex) {
                deSpiked += valid[i]
                continue
            }
            val prev = valid[i - 1]
            val cur = valid[i]
            val next = valid[i + 1]
            val pc = distance(prev, cur)
            val cn = distance(cur, next)
            val pn = distance(prev, next)
            val spike = pc > 250 && cn > 250 && pn < 140
            if (!spike) deSpiked += cur
        }

        val out = mutableListOf<LocationPoint>()
        for (p in deSpiked) {
            if (out.isEmpty()) {
                out += p
                continue
            }
            val prev = out.last()
            val dt = parseTime(p.recorded_at) - parseTime(prev.recorded_at)
            if (dt <= 0) continue
            val d = distance(prev, p)
            val kmh = d / (dt / 1000.0) * 3.6
            if (kmh <= MAX_SPEED_KMH || d < 150.0 || dt > SEGMENT_GAP_MS) out += p
        }
        return out
    }

    private fun splitSegments(points: List<LocationPoint>): List<List<LocationPoint>> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<LocationPoint>>()
        var current = mutableListOf(points.first())
        result += current
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dt = parseTime(b.recorded_at) - parseTime(a.recorded_at)
            val d = distance(a, b)
            val speed = if (dt > 0) d / (dt / 1000.0) * 3.6 else 999.0
            val breakLine = dt > SEGMENT_GAP_MS || (d > 600.0 && speed > 150.0)
            if (breakLine) {
                current = mutableListOf()
                result += current
            }
            current += b
        }
        return result.filter { it.size >= 2 }
    }

    private fun detectStops(points: List<LocationPoint>): List<StopInfo> {
        if (points.size < 2) return emptyList()
        val result = mutableListOf<StopInfo>()
        var i = 0
        var idx = 1
        while (i < points.size - 1) {
            val anchor = points[i]
            val cluster = mutableListOf(anchor)
            var j = i + 1
            while (j < points.size) {
                val centerLat = cluster.map { it.latitude.toDouble() }.average()
                val centerLon = cluster.map { it.longitude.toDouble() }.average()
                if (distance(centerLat, centerLon, points[j].latitude.toDouble(), points[j].longitude.toDouble()) <= STOP_RADIUS_M) {
                    cluster += points[j]
                    j++
                } else break
            }
            val duration = parseTime(cluster.last().recorded_at) - parseTime(cluster.first().recorded_at)
            if (cluster.size >= 2 && duration >= STOP_MIN_MS) {
                result += StopInfo(
                    index = idx++,
                    start = cluster.first(),
                    end = cluster.last(),
                    centerLat = cluster.map { it.latitude.toDouble() }.average(),
                    centerLon = cluster.map { it.longitude.toDouble() }.average(),
                    durationMs = duration
                )
                i = j.coerceAtLeast(i + 1)
            } else i++
        }
        return result
    }

    fun distance(a: LocationPoint, b: LocationPoint): Double = distance(
        a.latitude.toDouble(), a.longitude.toDouble(), b.latitude.toDouble(), b.longitude.toDouble()
    )

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = p2 - p1
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(p1) * cos(p2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun bearing(a: LocationPoint, b: LocationPoint): Float {
        val lat1 = Math.toRadians(a.latitude.toDouble())
        val lat2 = Math.toRadians(b.latitude.toDouble())
        val dLon = Math.toRadians(b.longitude.toDouble() - a.longitude.toDouble())
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    fun parseTime(value: String): Long {
        val clean = value.replace('T', ' ').replace("Z", "").take(19)
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(clean)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun formatDuration(ms: Long): String {
        val mins = (ms / 60000L).coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return when {
            h > 0 && m > 0 -> "${h}s ${m}d"
            h > 0 -> "${h}s"
            else -> "${m} dəq"
        }
    }

    fun formatKm(meters: Double): String = if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.US, "%.1f km", meters / 1000.0)
}
