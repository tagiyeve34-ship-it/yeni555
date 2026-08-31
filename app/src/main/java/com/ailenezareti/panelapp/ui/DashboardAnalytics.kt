package com.ailenezareti.panelapp.ui

import com.ailenezareti.panelapp.model.CallEntry
import com.ailenezareti.panelapp.model.GeoZone
import com.ailenezareti.panelapp.model.LocationPoint
import java.util.Locale
import kotlin.math.max


data class BatteryInsight(
    val startPct: Int?,
    val currentPct: Int?,
    val consumedPct: Int,
    val chargedPct: Int,
    val fastestDropPct: Int,
    val fastestStart: String?,
    val fastestEnd: String?,
    val fastestRatePerHour: Double,
    val chargeEvents: Int,
    val activityHint: String
)

data class PlaceSummary(
    val title: String,
    val subtitle: String,
    val totalMs: Long,
    val visits: Int,
    val latitude: Double,
    val longitude: Double
)

data class CallDashboard(
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val totalDurationSec: Int,
    val recent: List<CallEntry>,
    val topContacts: List<Pair<String, Int>>
)

data class SpeedInsight(val averageKmh: Double, val maxKmh: Double)

object DashboardAnalytics {

    fun battery(points: List<LocationPoint>): BatteryInsight {
        val src = points.sortedBy { RouteAnalytics.parseTime(it.recorded_at) }
            .filter { it.battery_pct != null }
        if (src.isEmpty()) return BatteryInsight(null, null, 0, 0, 0, null, null, 0.0, 0, "Məlumat yoxdur")

        var consumed = 0
        var charged = 0
        var chargeEvents = 0
        var fastestDrop = 0
        var fastestStart: String? = null
        var fastestEnd: String? = null
        var fastestRate = 0.0

        var episodeStart = 0
        var episodeDrop = 0
        for (i in 1 until src.size) {
            val prev = src[i - 1].battery_pct!!
            val cur = src[i].battery_pct!!
            val delta = cur - prev
            if (delta < 0) {
                val drop = -delta
                consumed += drop
                episodeDrop += drop
                val startTime = RouteAnalytics.parseTime(src[episodeStart].recorded_at)
                val endTime = RouteAnalytics.parseTime(src[i].recorded_at)
                val hours = max((endTime - startTime) / 3_600_000.0, 1.0 / 60.0)
                val rate = episodeDrop / hours
                if (episodeDrop > fastestDrop || (episodeDrop == fastestDrop && rate > fastestRate)) {
                    fastestDrop = episodeDrop
                    fastestStart = src[episodeStart].recorded_at
                    fastestEnd = src[i].recorded_at
                    fastestRate = rate
                }
            } else {
                if (delta > 0) {
                    charged += delta
                    chargeEvents++
                }
                episodeStart = i
                episodeDrop = 0
            }
        }

        val hint = when {
            fastestRate >= 10.0 -> "Yüksək enerji sərfiyyatı · aktiv istifadə ehtimalı yüksək"
            fastestRate >= 5.0 -> "Orta-yüksək enerji sərfiyyatı"
            consumed >= 25 -> "Gün ərzində nəzərəçarpan enerji sərfiyyatı"
            else -> "Batareya sərfiyyatı normal görünür"
        }
        return BatteryInsight(
            src.first().battery_pct,
            src.last().battery_pct,
            consumed,
            charged,
            fastestDrop,
            fastestStart,
            fastestEnd,
            fastestRate,
            chargeEvents,
            hint
        )
    }

    fun speed(points: List<LocationPoint>): SpeedInsight {
        val src = points.sortedBy { RouteAnalytics.parseTime(it.recorded_at) }
        var weightedDistance = 0.0
        var movingSeconds = 0.0
        var maxKmh = 0.0
        for (i in 1 until src.size) {
            val a = src[i - 1]
            val b = src[i]
            val dt = (RouteAnalytics.parseTime(b.recorded_at) - RouteAnalytics.parseTime(a.recorded_at)) / 1000.0
            if (dt <= 0 || dt > 15 * 60) continue
            val d = RouteAnalytics.distance(a, b)
            val kmh = d / dt * 3.6
            if (kmh > 180) continue
            if (d >= 20) {
                weightedDistance += d
                movingSeconds += dt
                if (kmh > maxKmh) maxKmh = kmh
            }
        }
        val avg = if (movingSeconds > 0) weightedDistance / movingSeconds * 3.6 else 0.0
        return SpeedInsight(avg, maxKmh)
    }

    fun calls(calls: List<CallEntry>): CallDashboard {
        fun kind(value: String): String {
            val t = value.lowercase(Locale.US)
            return when {
                "miss" in t || "cavab" in t || "burax" in t -> "missed"
                "out" in t || "ged" in t -> "outgoing"
                else -> "incoming"
            }
        }
        val incoming = calls.count { kind(it.call_type) == "incoming" }
        val outgoing = calls.count { kind(it.call_type) == "outgoing" }
        val missed = calls.count { kind(it.call_type) == "missed" }
        val top = calls.groupBy {
            it.contact_name?.takeIf(String::isNotBlank) ?: it.phone_number
        }.mapValues { (_, items) -> items.sumOf { it.duration_sec } }
            .entries.sortedByDescending { it.value }.take(4).map { it.key to it.value }
        return CallDashboard(
            incoming,
            outgoing,
            missed,
            calls.sumOf { it.duration_sec },
            calls.sortedByDescending { it.occurred_at }.take(5),
            top
        )
    }

    fun places(stops: List<StopInfo>, zones: List<GeoZone>): List<PlaceSummary> {
        data class Acc(
            var title: String,
            var subtitle: String,
            var total: Long,
            var visits: Int,
            var latSum: Double,
            var lonSum: Double
        )
        val groups = linkedMapOf<String, Acc>()
        stops.forEach { stop ->
            val zone = zones.filter { it.is_active == 1 }.minByOrNull {
                RouteAnalytics.distance(stop.centerLat, stop.centerLon, it.latitude.toDouble(), it.longitude.toDouble())
            }?.takeIf {
                RouteAnalytics.distance(stop.centerLat, stop.centerLon, it.latitude.toDouble(), it.longitude.toDouble()) <= max(120.0, it.radius_m.toDouble())
            }
            val key: String
            val title: String
            val subtitle: String
            if (zone != null) {
                key = "zone:${zone.id}"
                title = zone.name
                subtitle = "Təyin edilmiş zona"
            } else {
                val latBucket = String.format(Locale.US, "%.3f", stop.centerLat)
                val lonBucket = String.format(Locale.US, "%.3f", stop.centerLon)
                key = "gps:$latBucket:$lonBucket"
                title = "Dayanacaq"
                subtitle = "$latBucket, $lonBucket"
            }
            val a = groups.getOrPut(key) { Acc(title, subtitle, 0L, 0, 0.0, 0.0) }
            a.total += stop.durationMs
            a.visits++
            a.latSum += stop.centerLat
            a.lonSum += stop.centerLon
        }
        return groups.values.sortedByDescending { it.total }.take(5).map {
            PlaceSummary(it.title, it.subtitle, it.total, it.visits, it.latSum / it.visits, it.lonSum / it.visits)
        }
    }

    fun callDuration(sec: Int): String {
        if (sec <= 0) return "0 san"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h}s ${m}d"
            m > 0 -> "${m}d ${s}san"
            else -> "${s} san"
        }
    }

    fun clock(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        val clean = value.replace('T', ' ').take(19)
        return if (clean.length >= 16) clean.substring(11, 16) else clean
    }
}
