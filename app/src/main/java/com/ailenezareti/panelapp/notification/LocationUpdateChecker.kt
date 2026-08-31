package com.ailenezareti.panelapp.notification

import android.content.Context
import com.ailenezareti.panelapp.Prefs
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.model.ZoneCheckRequest
import java.text.SimpleDateFormat
import java.util.Locale

object LocationUpdateChecker {
    suspend fun check(context: Context, notifyOnChange: Boolean = true) {
        if (!Prefs.isLoggedIn(context)) return
        val api = ApiClient.get(context)
        val childrenResponse = api.getChildren()
        if (!childrenResponse.isSuccessful) return
        val children = childrenResponse.body()?.children ?: return

        for (child in children) {
            try {
                val response = api.getLocations(child.id, "3h")
                if (response.isSuccessful) {
                    val latest = response.body()?.locations?.firstOrNull()
                    if (latest != null) {
                        // GPS-in hər yenilənməsinə görə bildiriş ARTİQ YOXDUR.
                        Prefs.setLastLocationSeen(context, child.id, latest.recorded_at)
                        val batteryKey = "battery_${child.id}"
                        val battery = latest.battery_pct
                        if (battery != null && battery <= 15 && !Prefs.warningFlag(context, batteryKey)) {
                            Prefs.setWarningFlag(context, batteryKey, true)
                            if (notifyOnChange) LocationNotificationManager.showBatteryLow(context, child.id, child.name, battery)
                        } else if (battery != null && battery >= 20) Prefs.setWarningFlag(context, batteryKey, false)

                        val offlineKey = "offline_${child.id}"
                        val offline = minutesAgo(latest.recorded_at) > 30
                        if (offline && !Prefs.warningFlag(context, offlineKey)) {
                            Prefs.setWarningFlag(context, offlineKey, true)
                            if (notifyOnChange) LocationNotificationManager.showOffline(context, child.id, child.name, latest.recorded_at)
                        } else if (!offline) Prefs.setWarningFlag(context, offlineKey, false)
                    }
                }

                // Zona keçidlərini serverdə hesabla.
                val zoneEvents = api.checkZones(ZoneCheckRequest(child.id)).body()?.events.orEmpty()
                if (notifyOnChange) zoneEvents.forEach {
                    LocationNotificationManager.showZoneEvent(context, child.id, child.name, it.message, it.latitude, it.longitude, it.recorded_at, it.id)
                }

                // Yeni gələn / gedən zəngləri xəbərdar et.
                val calls = api.getCalls(child.id, null, null, "all", null, 100, 0).body()?.calls.orEmpty()
                val lastSeen = Prefs.lastCallSeen(context, child.id)
                if (calls.isNotEmpty()) {
                    val newest = calls.first().occurred_at
                    if (lastSeen.isBlank()) {
                        Prefs.setLastCallSeen(context, child.id, newest)
                    } else if (newest != lastSeen) {
                        val cut = calls.indexOfFirst { it.occurred_at == lastSeen }
                        val fresh = (if (cut >= 0) calls.take(cut) else calls.take(5)).reversed()
                        fresh.forEachIndexed { index, call ->
                            val allowed = when(call.call_type) {
                                "incoming" -> Prefs.notifyIncoming(context)
                                "outgoing" -> Prefs.notifyOutgoing(context)
                                "missed" -> Prefs.notifyMissed(context)
                                else -> false
                            }
                            if (allowed && notifyOnChange) {
                                val contact = call.contact_name?.takeIf { it.isNotBlank() } ?: call.phone_number
                                LocationNotificationManager.showCall(context, child.id, child.name, contact, call.call_type, call.duration_sec, call.occurred_at, index + newest.hashCode())
                            }
                        }
                        Prefs.setLastCallSeen(context, child.id, newest)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun minutesAgo(value: String): Long {
        val clean = value.replace("T", " ").replace("Z", "").substringBefore("+").take(19)
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(clean)
            ((System.currentTimeMillis() - (date?.time ?: 0)) / 60000L).coerceAtLeast(0)
        } catch (_: Exception) { 0 }
    }
}
