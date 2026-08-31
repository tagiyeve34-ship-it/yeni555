package com.ailenezareti.panelapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.databinding.DialogHistoryBinding
import com.ailenezareti.panelapp.databinding.FragmentLocationBinding
import com.ailenezareti.panelapp.model.LocationPoint
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class LocationFragment : Fragment(), Refreshable {

    private var _binding: FragmentLocationBinding? = null
    private val binding get() = _binding!!

    private var current: LocationPoint? = null
    private var selected: LocationPoint? = null
    private var routeStats: RouteStats? = null
    private var myPosition: GeoPoint? = null
    private var satelliteMode = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            loadMyLocation(true)
        } else {
            Toast.makeText(requireContext(), "Öz mövqeyini göstərmək üçün lokasiya icazəsi lazımdır", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val LAT = "lat"
        private const val LON = "lon"
        private const val TIME = "time"

        fun newInstance(lat: Double, lon: Double, time: String) = LocationFragment().apply {
            arguments = Bundle().apply {
                putDouble(LAT, lat)
                putDouble(LON, lon)
                putString(TIME, time)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(false)
        binding.mapView.maxZoomLevel = 21.0
        binding.mapView.minZoomLevel = 3.0
        binding.mapView.controller.setZoom(16.0)

        val sheetBehavior = BottomSheetBehavior.from(binding.bottomSheet).apply {
            isHideable = false
            isFitToContents = true
            peekHeight = (82 * resources.displayMetrics.density).toInt()
            state = BottomSheetBehavior.STATE_COLLAPSED
        }
        binding.locationCard.setOnClickListener {
            sheetBehavior.state = if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED)
                BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_EXPANDED
        }
        binding.dragHandle.setOnClickListener { binding.locationCard.performClick() }

        binding.standardMapButton.setOnClickListener { setMapMode(false) }
        binding.satelliteMapButton.setOnClickListener { setMapMode(true) }
        binding.zoomInButton.setOnClickListener { binding.mapView.controller.zoomIn() }
        binding.zoomOutButton.setOnClickListener { binding.mapView.controller.zoomOut() }
        binding.myLocationButton.setOnClickListener { loadMyLocation(true) }
        binding.historyButton.setOnClickListener { showHistoryDialog() }
        binding.gpsPointsButton.setOnClickListener { showLast24HoursPoints() }
        binding.recenterButton.setOnClickListener { focus(selected ?: current) }
        binding.googleMapsButton.setOnClickListener { openExternal(selected ?: current) }
        loadMyLocation(false)
        refresh()
    }

    override fun refresh() = loadLatest()

    private fun loadLatest() {
        val child = (activity as? MainActivity)?.activeChild() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latest = ApiClient.get(requireContext())
                    .getLocations(child.id, "3h")
                    .body()?.locations?.firstOrNull()

                launch(Dispatchers.Main) {
                    if (_binding == null || latest == null) return@launch
                    current = latest
                    selected = null
                    routeStats = null
                    drawLatestOnly(latest)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun drawLatestOnly(point: LocationPoint) {
        binding.mapView.overlays.clear()
        binding.routeSummaryText.visibility = View.GONE
        addCurrentMarker(point)
        addMyLocationMarker()
        showPointCard(point, "Son mövqe")
        focus(point)
        binding.mapView.invalidate()
    }

    private fun showLast24HoursPoints() {
        val child = (activity as? MainActivity)?.activeChild() ?: return
        val to = Date()
        val from = Date(to.time - 24L * 60L * 60L * 1000L)
        val apiFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        binding.gpsPointsButton.isEnabled = false
        binding.gpsPointsButton.text = "Yüklənir..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val points = ApiClient.get(requireContext())
                    .getLocations(child.id, "custom", apiFormat.format(from), apiFormat.format(to))
                    .body()?.locations.orEmpty()
                    .sortedByDescending { it.recorded_at }

                launch(Dispatchers.Main) {
                    if (_binding == null) return@launch
                    binding.gpsPointsButton.isEnabled = true
                    binding.gpsPointsButton.text = "GPS nöqtələri"
                    showGpsPointsDialog(points)
                }
            } catch (_: Exception) {
                launch(Dispatchers.Main) {
                    if (_binding == null) return@launch
                    binding.gpsPointsButton.isEnabled = true
                    binding.gpsPointsButton.text = "GPS nöqtələri"
                    Toast.makeText(requireContext(), "GPS nöqtələri yüklənmədi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showGpsPointsDialog(points: List<LocationPoint>) {
        if (points.isEmpty()) {
            Toast.makeText(requireContext(), "Son 24 saatda GPS nöqtəsi yoxdur", Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = FrameLayout(requireContext()).apply {
            setPadding(dp(10), dp(4), dp(10), dp(10))
        }
        val recycler = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = GpsPointAdapter(points) { point ->
                selected = point
                drawSelectedGpsPoint(point)
                gpsPointsDialog?.dismiss()
            }
        }
        container.addView(
            recycler,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(520)
            )
        )

        gpsPointsDialog = AlertDialog.Builder(requireContext())
            .setTitle("Son 24 saat · ${points.size} GPS nöqtəsi")
            .setView(container)
            .setNegativeButton("Bağla", null)
            .create()
        gpsPointsDialog?.show()
    }

    private var gpsPointsDialog: AlertDialog? = null

    private fun drawSelectedGpsPoint(point: LocationPoint) {
        binding.mapView.overlays.clear()
        binding.routeSummaryText.visibility = View.GONE
        addBadgeMarker(point, "•", Color.parseColor("#0F9D8B"), "GPS nöqtəsi")
        addMyLocationMarker()
        showPointCard(point, "GPS nöqtəsi")
        focus(point)
        binding.mapView.invalidate()
    }

    private inner class GpsPointAdapter(
        private val points: List<LocationPoint>,
        private val onClick: (LocationPoint) -> Unit
    ) : RecyclerView.Adapter<GpsPointAdapter.Holder>() {

        inner class Holder(val card: MaterialCardView, val text: TextView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val density = resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            val textView = TextView(parent.context).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setTextColor(ContextCompat.getColor(parent.context, R.color.text))
                textSize = 15f
            }
            val card = MaterialCardView(parent.context).apply {
                radius = dp(14).toFloat()
                cardElevation = dp(1).toFloat()
                strokeWidth = dp(1)
                strokeColor = ContextCompat.getColor(parent.context, R.color.teal)
                setCardBackgroundColor(ContextCompat.getColor(parent.context, R.color.white))
                addView(textView)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(2), dp(5), dp(2), dp(5))
                }
            }
            return Holder(card, textView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val point = points[position]
            val battery = point.battery_pct?.let { "$it%" } ?: "—"
            val accuracy = point.accuracy_m?.let { "±$it m" } ?: "—"
            holder.text.text = buildString {
                append(point.recorded_at.replace('T', ' ').take(19))
                append("   •   🔋 ")
                append(battery)
                append("   •   GPS ")
                append(accuracy)
                append("\n")
                append(point.latitude)
                append(", ")
                append(point.longitude)
                append("   ›")
            }
            holder.card.setOnClickListener { onClick(point) }
        }

        override fun getItemCount(): Int = points.size
    }

    private fun showHistoryDialog() {
        val dialogBinding = DialogHistoryBinding.inflate(layoutInflater)
        val now = Calendar.getInstance()
        val start = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -3) }
        var from = start.time
        var to = now.time
        val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        dialogBinding.fromButton.setText(displayFormat.format(from))
        dialogBinding.toButton.setText(displayFormat.format(to))

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.showRouteButton.setOnClickListener {
            val parsedFrom = try {
                displayFormat.parse(dialogBinding.fromButton.text.toString())
            } catch (_: Exception) { null }
            val parsedTo = try {
                displayFormat.parse(dialogBinding.toButton.text.toString())
            } catch (_: Exception) { null }

            if (parsedFrom == null || parsedTo == null || parsedFrom.after(parsedTo)) {
                Toast.makeText(
                    requireContext(),
                    "Tarixi belə yaz: 16.08.2026 21:35",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            from = parsedFrom
            to = parsedTo
            dialog.dismiss()
            loadHistory(from, to, dialogBinding.pointsSwitch.isChecked, dialogBinding.routeSwitch.isChecked)
        }
        dialog.show()
    }

    private fun loadHistory(from: Date, to: Date, showPoints: Boolean, showRoute: Boolean) {
        val child = (activity as? MainActivity)?.activeChild() ?: return
        val apiFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val raw = ApiClient.get(requireContext())
                    .getLocations(child.id, "custom", apiFormat.format(from), apiFormat.format(to))
                    .body()?.locations.orEmpty()
                val stats = RouteAnalytics.analyze(raw)

                launch(Dispatchers.Main) {
                    if (_binding == null) return@launch
                    routeStats = stats
                    drawHistory(stats, showPoints, showRoute)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun drawHistory(stats: RouteStats, showPoints: Boolean, showRoute: Boolean) {
        binding.mapView.overlays.clear()
        val points = stats.points
        if (points.isEmpty()) {
            current?.let { drawLatestOnly(it) }
            Toast.makeText(requireContext(), "Bu intervalda məlumat yoxdur", Toast.LENGTH_SHORT).show()
            return
        }

        if (showRoute) {
            drawSmartRoute(stats.segments)
            addDirectionMarkers(stats.segments)
        }

        if (showPoints) addSmallRoutePoints(points)

        addBadgeMarker(points.first(), "A", Color.parseColor("#34A853"), "Başlanğıc")
        stats.stops.forEach { stop -> addStopMarker(stop) }
        addBadgeMarker(points.last(), "S", Color.parseColor("#EA4335"), "Son nöqtə")
        addMyLocationMarker()

        selected = null
        showPointCard(points.last(), "Son nöqtə")
        binding.routeSummaryText.text = buildString {
            append(RouteAnalytics.formatKm(stats.distanceMeters))
            append("  •  ")
            append(stats.stops.size)
            append(" dayanacaq  •  hərəkət ")
            append(RouteAnalytics.formatDuration(stats.movingMs))
            append("\nYaşıl: yavaş  •  Mavi: hərəkət  •  Narıncı: sürətli")
        }
        binding.routeSummaryText.visibility = View.VISIBLE

        val allGeo = points.map { geo(it) }
        if (allGeo.size == 1) {
            focus(points.first())
        } else {
            val box = BoundingBox.fromGeoPoints(allGeo)
            binding.mapView.post { binding.mapView.zoomToBoundingBox(box, false, 90) }
        }
        binding.mapView.invalidate()
    }


    private fun drawSmartRoute(segments: List<List<LocationPoint>>) {
        // Soft white halo keeps the route visible on streets and satellite imagery.
        segments.forEach { segment ->
            if (segment.size < 2) return@forEach
            binding.mapView.overlays.add(Polyline().apply {
                setPoints(segment.map { geo(it) })
                outlinePaint.color = Color.WHITE
                outlinePaint.strokeWidth = 18f
                outlinePaint.alpha = 210
            })
        }

        // Color contiguous movement legs by estimated speed, but group equal colors
        // so long histories do not create thousands of overlays.
        segments.forEach { segment ->
            if (segment.size < 2) return@forEach
            var runColor: Int? = null
            var runPoints = mutableListOf<LocationPoint>()

            fun flush() {
                val color = runColor ?: return
                if (runPoints.size < 2) return
                binding.mapView.overlays.add(Polyline().apply {
                    setPoints(runPoints.map { geo(it) })
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 11f
                    outlinePaint.alpha = 245
                })
            }

            for (i in 1 until segment.size) {
                val a = segment[i - 1]
                val b = segment[i]
                val dtSec = (RouteAnalytics.parseTime(b.recorded_at) - RouteAnalytics.parseTime(a.recorded_at)) / 1000.0
                if (dtSec <= 0) continue
                val kmh = RouteAnalytics.distance(a, b) / dtSec * 3.6
                val color = when {
                    kmh >= 45 -> Color.parseColor("#F97316")
                    kmh >= 16 -> Color.parseColor("#356DF3")
                    else -> Color.parseColor("#0F9D94")
                }
                if (runColor == null) {
                    runColor = color
                    runPoints.add(a)
                    runPoints.add(b)
                } else if (runColor == color) {
                    runPoints.add(b)
                } else {
                    flush()
                    runColor = color
                    runPoints = mutableListOf(a, b)
                }
            }
            flush()
        }
    }

    private fun addSmallRoutePoints(points: List<LocationPoint>) {
        val step = max(1, points.size / 80)
        points.forEachIndexed { index, point ->
            if (index % step != 0 || index == 0 || index == points.lastIndex) return@forEachIndexed
            val marker = Marker(binding.mapView).apply {
                position = geo(point)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.map_dot)
                setOnMarkerClickListener { _, _ ->
                    selected = point
                    showPointCard(point, "Marşrut nöqtəsi")
                    true
                }
            }
            binding.mapView.overlays.add(marker)
        }
    }

    private fun addDirectionMarkers(segments: List<List<LocationPoint>>) {
        val pairs = segments.flatMap { segment ->
            if (segment.size < 2) emptyList() else (1 until segment.size).map { segment[it - 1] to segment[it] }
        }
        if (pairs.isEmpty()) return
        val step = max(1, pairs.size / 6)
        pairs.forEachIndexed { index, pair ->
            if (index % step != 0) return@forEachIndexed
            val marker = Marker(binding.mapView).apply {
                position = geo(pair.second)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.map_direction)
                rotation = RouteAnalytics.bearing(pair.first, pair.second)
                setOnMarkerClickListener { _, _ ->
                    selected = pair.second
                    showPointCard(pair.second, "Hərəkət istiqaməti")
                    true
                }
            }
            binding.mapView.overlays.add(marker)
        }
    }

    private fun addCurrentMarker(point: LocationPoint) {
        val marker = Marker(binding.mapView).apply {
            position = geo(point)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.map_pin)
            setOnMarkerClickListener { _, _ ->
                selected = point
                showPointCard(point, "Son mövqe")
                true
            }
        }
        binding.mapView.overlays.add(marker)
    }

    private fun addBadgeMarker(point: LocationPoint, text: String, color: Int, title: String) {
        val marker = Marker(binding.mapView).apply {
            position = geo(point)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = makeBadge(text, color, 50)
            setOnMarkerClickListener { _, _ ->
                selected = point
                showPointCard(point, title)
                true
            }
        }
        binding.mapView.overlays.add(marker)
    }

    private fun addStopMarker(stop: StopInfo) {
        val representative = stop.end
        val marker = Marker(binding.mapView).apply {
            position = GeoPoint(stop.centerLat, stop.centerLon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = makeBadge(stop.index.toString(), Color.parseColor("#F59E0B"), 46)
            setOnMarkerClickListener { _, _ ->
                selected = representative
                showPointCard(
                    representative,
                    "Dayanacaq ${stop.index}",
                    "${formatClock(stop.start.recorded_at)}–${formatClock(stop.end.recorded_at)}  •  ${RouteAnalytics.formatDuration(stop.durationMs)}  •  100 m radius"
                )
                true
            }
        }
        binding.mapView.overlays.add(marker)
    }

    private fun makeBadge(text: String, color: Int, sizeDp: Int): BitmapDrawable {
        val density = resources.displayMetrics.density
        val size = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size * 0.49f, paint)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size * 0.41f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = size * 0.38f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, size / 2f, y, paint)
        return BitmapDrawable(resources, bitmap)
    }

    private fun showPointCard(point: LocationPoint, title: String, detail: String? = null) {
        binding.titleText.text = title
        binding.timeText.text = "Son yenilənmə: ${point.recorded_at}"
        binding.accuracyText.text = "GPS dəqiqliyi: ${point.accuracy_m ?: "—"} m"
        binding.batteryText.text = "${point.battery_pct ?: 0}%"
        binding.detailText.text = detail.orEmpty()
        binding.detailText.visibility = if (detail.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun focus(point: LocationPoint?) {
        point ?: return
        binding.mapView.controller.setCenter(geo(point))
        binding.mapView.controller.setZoom(if (satelliteMode) 19.0 else 20.0)
    }

    private fun setMapMode(satellite: Boolean) {
        satelliteMode = satellite
        if (satellite) {
            // Esri World Imagery: higher-detail satellite/aerial imagery than Sentinel-2.
            binding.mapView.setTileSource(
                object : OnlineTileSourceBase(
                    "Esri-World-Imagery",
                    0, 19, 256, "",
                    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
                ) {
                    override fun getTileURLString(pMapTileIndex: Long): String {
                        // ArcGIS cached map tiles use {z}/{y}/{x}.
                        return getBaseUrl() +
                            MapTileIndex.getZoom(pMapTileIndex) + "/" +
                            MapTileIndex.getY(pMapTileIndex) + "/" +
                            MapTileIndex.getX(pMapTileIndex)
                    }
                }
            )
            if (binding.mapView.zoomLevelDouble > 19.0) binding.mapView.controller.setZoom(19.0)
        } else {
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        }
        binding.standardMapButton.isEnabled = satellite
        binding.satelliteMapButton.isEnabled = !satellite
        binding.mapView.invalidate()
    }

    private fun loadMyLocation(centerOnMe: Boolean) {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            if (centerOnMe) locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val manager = ctx.getSystemService(LocationManager::class.java)
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        val best = candidates.maxByOrNull { it.time }
        if (best != null) {
            myPosition = GeoPoint(best.latitude, best.longitude)
            redrawKeepingCurrentView()
            if (centerOnMe) {
                val childPoint = selected ?: current
                if (childPoint != null) {
                    val box = BoundingBox.fromGeoPoints(listOf(myPosition!!, geo(childPoint)))
                    binding.mapView.post { binding.mapView.zoomToBoundingBox(box, true, 120) }
                } else binding.mapView.controller.setCenter(myPosition)
            }
        } else if (centerOnMe) {
            Toast.makeText(ctx, "Telefonun cari lokasiyası hələ alınmayıb", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redrawKeepingCurrentView() {
        val center = binding.mapView.mapCenter
        val zoom = binding.mapView.zoomLevelDouble
        routeStats?.let { drawHistory(it, true, true) } ?: current?.let { drawLatestOnly(it) }
        binding.mapView.controller.setCenter(center)
        binding.mapView.controller.setZoom(zoom)
    }

    private fun addMyLocationMarker() {
        val mine = myPosition ?: return
        val marker = Marker(binding.mapView).apply {
            position = mine
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = makeBadge("M", Color.parseColor("#7B4EE4"), 48)
            title = "Mənim telefonum"
            subDescription = "Panel telefonunun mövqeyi"
        }
        binding.mapView.overlays.add(marker)
    }

    private fun openExternal(point: LocationPoint?) {
        point ?: return
        val lat = point.latitude
        val lon = point.longitude
        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, geoUri))
        } catch (_: Exception) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
                )
            )
        }
    }

    private fun geo(point: LocationPoint) = GeoPoint(point.latitude.toDouble(), point.longitude.toDouble())

    private fun formatClock(value: String): String {
        val clean = value.replace('T', ' ').take(19)
        return if (clean.length >= 16) clean.substring(11, 16) else clean
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        gpsPointsDialog?.dismiss()
        gpsPointsDialog = null
        binding.mapView.overlays.clear()
        _binding = null
        super.onDestroyView()
    }
}
