package com.ailenezareti.panelapp.ui

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.databinding.FragmentZonesBinding
import com.ailenezareti.panelapp.model.GeoZone
import com.ailenezareti.panelapp.model.ZoneDeleteRequest
import com.ailenezareti.panelapp.model.ZoneSaveRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class ZonesFragment : Fragment(), Refreshable {
    private var _b: FragmentZonesBinding? = null
    private val b get() = _b!!
    private var selected: GeoPoint? = null
    private var previewCircle: Polygon? = null
    private var previewMarker: Marker? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _b = FragmentZonesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.zoneMap.setTileSource(TileSourceFactory.MAPNIK)
        b.zoneMap.setMultiTouchControls(true)
        b.zoneMap.controller.setZoom(15.0)
        b.radiusSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { updateRadius() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        b.zoneMap.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { selectCenter(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean { selectCenter(p); return true }
        }))
        b.saveZoneButton.setOnClickListener { saveZone() }
        refresh()
    }

    private fun radius(): Int = 50 + b.radiusSeek.progress
    private fun updateRadius() {
        b.radiusText.text = "${radius()} m"
        selected?.let { center ->
            previewCircle?.points = Polygon.pointsAsCircle(center, radius().toDouble())
        }
        b.zoneMap.invalidate()
    }

    private fun selectCenter(p: GeoPoint) {
        selected = p
        previewMarker?.let { b.zoneMap.overlays.remove(it) }
        previewCircle?.let { b.zoneMap.overlays.remove(it) }
        previewCircle = Polygon(b.zoneMap).apply {
            points = Polygon.pointsAsCircle(p, radius().toDouble())
            fillPaint.color = Color.argb(40, 19, 128, 122)
            outlinePaint.color = Color.rgb(19, 128, 122)
            outlinePaint.strokeWidth = 3f
        }
        previewMarker = Marker(b.zoneMap).apply {
            position = p; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.map_pin)
        }
        b.zoneMap.overlays.add(previewCircle); b.zoneMap.overlays.add(previewMarker)
        b.zoneMap.invalidate()
    }

    private fun saveZone() {
        val child = (activity as? MainActivity)?.activeChild() ?: return
        val p = selected ?: run { Toast.makeText(requireContext(), "Xəritədə zona mərkəzini seç", Toast.LENGTH_SHORT).show(); return }
        val name = b.zoneNameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { b.zoneNameInput.error = "Zona adı yaz"; return }
        val req = ZoneSaveRequest(null, child.id, name, p.latitude, p.longitude, radius(), b.notifyEnterSwitch.isChecked, b.notifyExitSwitch.isChecked)
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = try { ApiClient.get(requireContext()).createZone(req).isSuccessful } catch (_: Exception) { false }
            launch(Dispatchers.Main) {
                if (ok) { Toast.makeText(requireContext(), "Zona yadda saxlandı", Toast.LENGTH_SHORT).show(); b.zoneNameInput.setText(""); refresh() }
                else Toast.makeText(requireContext(), "Zona yadda saxlanmadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun refresh() {
        val child = (activity as? MainActivity)?.activeChild() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val zones = ApiClient.get(requireContext()).getZones(child.id).body()?.zones.orEmpty()
                val last = ApiClient.get(requireContext()).getLocations(child.id, "3h").body()?.locations?.firstOrNull()
                launch(Dispatchers.Main) { if (_b != null) render(zones, last?.latitude?.toDoubleOrNull(), last?.longitude?.toDoubleOrNull()) }
            } catch (_: Exception) { }
        }
    }

    private fun render(zones: List<GeoZone>, lat: Double?, lon: Double?) {
        // MapEventsOverlay-ni saxla, qalan zona overlaylərini yenidən çək.
        val events = b.zoneMap.overlays.filterIsInstance<MapEventsOverlay>().firstOrNull()
        b.zoneMap.overlays.clear(); if (events != null) b.zoneMap.overlays.add(events)
        zones.forEach { z ->
            val p = GeoPoint(z.latitude.toDouble(), z.longitude.toDouble())
            b.zoneMap.overlays.add(Polygon(b.zoneMap).apply {
                points = Polygon.pointsAsCircle(p, z.radius_m.toDouble())
                fillPaint.color = Color.argb(28, 19, 128, 122)
                outlinePaint.color = Color.rgb(19, 128, 122)
                outlinePaint.strokeWidth = 3f
            })
            b.zoneMap.overlays.add(Marker(b.zoneMap).apply {
                position=p; title="${z.name} · ${z.radius_m} m"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon=androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.map_pin)
            })
        }
        if (selected != null) selectCenter(selected!!)
        else if (lat != null && lon != null) { val p=GeoPoint(lat,lon); b.zoneMap.controller.setCenter(p) }
        b.zoneMap.invalidate()

        b.zonesContainer.removeAllViews()
        if (zones.isEmpty()) {
            b.zonesContainer.addView(TextView(requireContext()).apply { text="Hələ zona yaradılmayıb"; setTextColor(Color.GRAY); setPadding(0,12,0,12) })
        } else zones.forEach { z -> addZoneRow(z) }
    }

    private fun addZoneRow(z: GeoZone) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0,12,0,12); gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val zoneLabel = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            this.text = "${z.name}\n${z.radius_m} m · ${if (z.notify_exit == 1) "çıxış ON" else "çıxış OFF"}${if (z.notify_enter == 1) " · giriş ON" else ""}"
            setTextColor(resources.getColor(R.color.text, null))
            textSize = 15f
            setOnClickListener {
                val point = GeoPoint(z.latitude.toDouble(), z.longitude.toDouble())
                b.zoneMap.controller.animateTo(point)
                b.zoneMap.controller.setZoom(16.5)
            }
        }
        val del = TextView(requireContext()).apply {
            text="Sil"; setTextColor(Color.rgb(190,50,50)); setPadding(24,12,8,12)
            setOnClickListener { deleteZone(z) }
        }
        row.addView(zoneLabel); row.addView(del); b.zonesContainer.addView(row)
    }

    private fun deleteZone(z: GeoZone) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = try { ApiClient.get(requireContext()).deleteZone(ZoneDeleteRequest(z.id, z.child_id)).isSuccessful } catch (_: Exception) { false }
            launch(Dispatchers.Main) { if (ok) refresh() }
        }
    }

    override fun onResume() { super.onResume(); b.zoneMap.onResume() }
    override fun onPause() { b.zoneMap.onPause(); super.onPause() }
    override fun onDestroyView() { _b=null; super.onDestroyView() }
}
