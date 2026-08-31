package com.ailenezareti.panelapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.databinding.FragmentHomeBinding
import com.ailenezareti.panelapp.model.CallEntry
import com.ailenezareti.panelapp.model.GeoZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeFragment : Fragment(), Refreshable {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.openMapButton.setOnClickListener { (activity as? MainActivity)?.openLocationTab() }
        refresh()
    }

    override fun refresh() {
        val child = (activity as? MainActivity)?.activeChild() ?: return
        val now = Calendar.getInstance()
        binding.dateText.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(now.time)

        val apiFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStart = startOfDay(now)
        val todayEnd = now.time
        val yesterdayStartCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStart = startOfDay(yesterdayStartCal)
        val yesterdayEnd = (yesterdayStartCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 0)
        }.time

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.get(requireContext())
                val todayPoints = api.getLocations(child.id, "custom", apiFormat.format(todayStart), apiFormat.format(todayEnd)).body()?.locations.orEmpty()
                val yesterdayPoints = api.getLocations(child.id, "custom", apiFormat.format(yesterdayStart), apiFormat.format(yesterdayEnd)).body()?.locations.orEmpty()
                val todayCalls = api.getCalls(child.id, dateOnly.format(todayStart), dateOnly.format(todayEnd), "all", null, 500, 0).body()?.calls.orEmpty()
                val yesterdayCalls = api.getCalls(child.id, dateOnly.format(yesterdayStart), dateOnly.format(yesterdayEnd), "all", null, 500, 0).body()?.calls.orEmpty()
                val zones = api.getZones(child.id).body()?.zones.orEmpty()

                val todayStats = RouteAnalytics.analyze(todayPoints)
                val yesterdayStats = RouteAnalytics.analyze(yesterdayPoints)
                val battery = DashboardAnalytics.battery(todayStats.points)
                val speed = DashboardAnalytics.speed(todayStats.points)
                val callStats = DashboardAnalytics.calls(todayCalls)
                val places = DashboardAnalytics.places(todayStats.stops, zones)

                launch(Dispatchers.Main) {
                    if (_binding == null) return@launch
                    render(
                        todayStats,
                        yesterdayStats,
                        battery,
                        speed,
                        callStats,
                        todayCalls,
                        yesterdayCalls,
                        places,
                        zones
                    )
                }
            } catch (_: Exception) {
                launch(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.liveStatusText.text = "Məlumat alınmadı"
                        binding.liveStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
                    }
                }
            }
        }
    }

    private fun render(
        today: RouteStats,
        yesterday: RouteStats,
        battery: BatteryInsight,
        speed: SpeedInsight,
        callStats: CallDashboard,
        todayCalls: List<CallEntry>,
        yesterdayCalls: List<CallEntry>,
        places: List<PlaceSummary>,
        zones: List<GeoZone>
    ) {
        val latest = today.points.lastOrNull()
        binding.distanceText.text = RouteAnalytics.formatKm(today.distanceMeters)
        binding.movingText.text = RouteAnalytics.formatDuration(today.movingMs)
        binding.stoppedText.text = RouteAnalytics.formatDuration(today.stoppedMs)
        binding.stopsCountText.text = today.stops.size.toString()
        binding.avgSpeedText.text = "${speed.averageKmh.roundToInt()} km/s"
        binding.maxSpeedText.text = "Maks ${speed.maxKmh.roundToInt()} km/s"
        binding.callsText.text = todayCalls.size.toString()

        binding.distanceDeltaText.text = "Dünən ${RouteAnalytics.formatKm(yesterday.distanceMeters)}"
        binding.movingDeltaText.text = "Dünən ${RouteAnalytics.formatDuration(yesterday.movingMs)}"
        binding.stoppedDeltaText.text = "Dünən ${RouteAnalytics.formatDuration(yesterday.stoppedMs)}"
        binding.stopsDeltaText.text = "Dünən ${yesterday.stops.size}"
        binding.callsDeltaText.text = "Dünən ${yesterdayCalls.size}"

        if (today.points.isNotEmpty()) {
            binding.firstLastText.text = "${DashboardAnalytics.clock(today.points.first().recorded_at)} → ${DashboardAnalytics.clock(today.points.last().recorded_at)}"
        } else binding.firstLastText.text = "Məlumat yoxdur"

        if (latest != null) {
            binding.lastUpdateText.text = DashboardAnalytics.clock(latest.recorded_at)
            binding.currentBatteryText.text = latest.battery_pct?.let { "$it%" } ?: "—"
            binding.gpsAccuracyText.text = latest.accuracy_m?.let { "±$it m" } ?: "—"
            binding.deviceText.text = "Son mövqe: ${latest.latitude}, ${latest.longitude}"
            setStatus(latest.recorded_at)
        } else {
            binding.lastUpdateText.text = "—"
            binding.currentBatteryText.text = "—"
            binding.gpsAccuracyText.text = "—"
            binding.deviceText.text = "Bu gün GPS məlumatı yoxdur"
            binding.liveStatusText.text = "● Offline"
            binding.liveStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
        }

        renderBattery(battery)
        renderCalls(callStats)
        renderStops(today.stops, zones)
        renderPlaces(places)
        renderComparison(today, yesterday, todayCalls.size, yesterdayCalls.size)
    }

    private fun renderBattery(b: BatteryInsight) {
        binding.batteryTodayText.text = b.currentPct?.let { "$it%" } ?: "—"
        if (b.startPct == null || b.currentPct == null) {
            binding.batterySummaryText.text = "Bu gün batareya məlumatı yoxdur"
            binding.batteryDetailText.text = "GPS qeydlərində batareya faizi görünəndə analiz avtomatik yaranacaq."
            binding.batteryInsightText.text = "Analiz üçün kifayət qədər məlumat yoxdur"
            return
        }
        binding.batterySummaryText.text = "${b.startPct}% → ${b.currentPct}%   •   sərfiyyat ${b.consumedPct}%"
        val fastest = if (b.fastestDropPct > 0 && b.fastestStart != null && b.fastestEnd != null) {
            "Ən sürətli azalma: ${DashboardAnalytics.clock(b.fastestStart)}–${DashboardAnalytics.clock(b.fastestEnd)} · −${b.fastestDropPct}% · təx. ${b.fastestRatePerHour.roundToInt()}%/saat"
        } else "Kəskin azalma qeydə alınmayıb"
        val charge = if (b.chargedPct > 0) " • ehtimal olunan şarj artımı +${b.chargedPct}%" else ""
        binding.batteryDetailText.text = fastest + charge
        binding.batteryInsightText.text = "Ağıllı analiz: ${b.activityHint}. Bu ehtimal yalnız batareya faizinin dəyişməsinə əsaslanır."
    }

    private fun renderCalls(c: CallDashboard) {
        binding.incomingCallsText.text = "↙ ${c.incoming}\nGələn"
        binding.outgoingCallsText.text = "↗ ${c.outgoing}\nGedən"
        binding.missedCallsText.text = "× ${c.missed}\nCavabsız"
        binding.totalCallDurationText.text = "Danışıq ${DashboardAnalytics.callDuration(c.totalDurationSec)}"

        binding.recentCallsContainer.removeAllViews()
        if (c.recent.isEmpty()) {
            binding.recentCallsContainer.addView(simpleMuted("Bu gün zəng qeydi yoxdur"))
        } else c.recent.forEach { call ->
            val name = call.contact_name?.takeIf(String::isNotBlank) ?: call.phone_number
            val type = callTypeLabel(call.call_type)
            val duration = if (call.duration_sec > 0) DashboardAnalytics.callDuration(call.duration_sec) else "—"
            val row = dashboardRow(
                title = name,
                subtitle = "${call.phone_number} · $type · $duration",
                right = DashboardAnalytics.clock(call.occurred_at)
            )
            binding.recentCallsContainer.addView(row)
        }

        binding.topContactsContainer.removeAllViews()
        if (c.topContacts.isEmpty()) binding.topContactsContainer.addView(simpleMuted("Danışıq müddəti yoxdur"))
        else c.topContacts.forEachIndexed { index, item ->
            binding.topContactsContainer.addView(
                dashboardRow("${index + 1}. ${item.first}", "Ümumi danışıq", DashboardAnalytics.callDuration(item.second))
            )
        }
    }

    private fun renderStops(stops: List<StopInfo>, zones: List<GeoZone>) {
        binding.stopsContainer.removeAllViews()
        binding.noStopsText.visibility = if (stops.isEmpty()) View.VISIBLE else View.GONE
        stops.take(6).forEach { stop ->
            val zoneName = zoneFor(stop, zones)?.name
            val title = zoneName ?: "Dayanacaq ${stop.index}"
            val subtitle = "${DashboardAnalytics.clock(stop.start.recorded_at)}–${DashboardAnalytics.clock(stop.end.recorded_at)} · ${RouteAnalytics.formatDuration(stop.durationMs)}"
            val right = if (zoneName == null) String.format(Locale.US, "%.4f, %.4f", stop.centerLat, stop.centerLon) else "Zona"
            val row = dashboardRow(title, subtitle, right)
            row.setOnClickListener { (activity as? MainActivity)?.openLocationTab() }
            binding.stopsContainer.addView(row)
        }
    }

    private fun renderPlaces(places: List<PlaceSummary>) {
        binding.placesContainer.removeAllViews()
        if (places.isEmpty()) {
            binding.placesContainer.addView(simpleMuted("Bu gün kifayət qədər dayanacaq məlumatı yoxdur"))
            return
        }
        places.forEachIndexed { index, place ->
            val subtitle = "${place.visits} dəfə · ${place.subtitle}"
            binding.placesContainer.addView(
                dashboardRow("${index + 1}. ${place.title}", subtitle, RouteAnalytics.formatDuration(place.totalMs))
            )
        }
    }

    private fun renderComparison(today: RouteStats, yesterday: RouteStats, todayCalls: Int, yesterdayCalls: Int) {
        binding.comparisonContainer.removeAllViews()
        comparisonRow("Məsafə", RouteAnalytics.formatKm(today.distanceMeters), RouteAnalytics.formatKm(yesterday.distanceMeters), today.distanceMeters - yesterday.distanceMeters)
        comparisonRow("Hərəkətdə", RouteAnalytics.formatDuration(today.movingMs), RouteAnalytics.formatDuration(yesterday.movingMs), (today.movingMs - yesterday.movingMs).toDouble())
        comparisonRow("Dayanma", RouteAnalytics.formatDuration(today.stoppedMs), RouteAnalytics.formatDuration(yesterday.stoppedMs), (today.stoppedMs - yesterday.stoppedMs).toDouble())
        comparisonRow("Dayanacaq sayı", today.stops.size.toString(), yesterday.stops.size.toString(), (today.stops.size - yesterday.stops.size).toDouble())
        comparisonRow("Zəng sayı", todayCalls.toString(), yesterdayCalls.toString(), (todayCalls - yesterdayCalls).toDouble())
    }

    private fun comparisonRow(label: String, today: String, yesterday: String, delta: Double) {
        val arrow = when {
            delta > 0.01 -> "↑"
            delta < -0.01 -> "↓"
            else -> "="
        }
        val row = dashboardRow(label, "Bu gün $today · Dünən $yesterday", arrow)
        binding.comparisonContainer.addView(row)
    }

    private fun dashboardRow(title: String, subtitle: String, right: String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(10))
        }
        val left = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(requireContext()).apply {
            text = title
            textSize = 13.5f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        left.addView(TextView(requireContext()).apply {
            text = subtitle
            textSize = 11f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
            setPadding(0, dp(3), 0, 0)
            maxLines = 2
        })
        row.addView(left)
        row.addView(TextView(requireContext()).apply {
            text = right
            textSize = 11.5f
            gravity = android.view.Gravity.END
            setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_dark))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
        })
        return row
    }

    private fun simpleMuted(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 12f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
        setPadding(0, dp(10), 0, dp(8))
    }

    private fun callTypeLabel(value: String): String {
        val t = value.lowercase(Locale.US)
        return when {
            "miss" in t || "cavab" in t || "burax" in t -> "Cavabsız"
            "out" in t || "ged" in t -> "Gedən"
            else -> "Gələn"
        }
    }

    private fun zoneFor(stop: StopInfo, zones: List<GeoZone>): GeoZone? = zones
        .filter { it.is_active == 1 }
        .minByOrNull { RouteAnalytics.distance(stop.centerLat, stop.centerLon, it.latitude.toDouble(), it.longitude.toDouble()) }
        ?.takeIf {
            RouteAnalytics.distance(stop.centerLat, stop.centerLon, it.latitude.toDouble(), it.longitude.toDouble()) <= kotlin.math.max(120.0, it.radius_m.toDouble())
        }

    private fun setStatus(recordedAt: String) {
        val age = System.currentTimeMillis() - RouteAnalytics.parseTime(recordedAt)
        val (text, color) = when {
            age <= 5 * 60 * 1000L -> "● Canlı" to R.color.success
            age <= 30 * 60 * 1000L -> "● Gecikir" to R.color.warning
            else -> "● Offline" to R.color.danger
        }
        binding.liveStatusText.text = text
        binding.liveStatusText.setTextColor(ContextCompat.getColor(requireContext(), color))
    }

    private fun startOfDay(calendar: Calendar) = (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
