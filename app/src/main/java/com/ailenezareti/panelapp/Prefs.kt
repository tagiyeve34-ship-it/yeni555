package com.ailenezareti.panelapp

import android.content.Context

object Prefs {
    private const val FILE = "panel_prefs"
    private const val KEY_TOKEN = "api_token"
    private const val KEY_PARENT_NAME = "parent_name"
    private const val KEY_ACTIVE_CHILD = "active_child_id"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun token(ctx: Context): String = prefs(ctx).getString(KEY_TOKEN, "") ?: ""
    fun setToken(ctx: Context, token: String) = prefs(ctx).edit().putString(KEY_TOKEN, token).apply()
    fun clearToken(ctx: Context) = prefs(ctx).edit().remove(KEY_TOKEN).apply()
    fun isLoggedIn(ctx: Context): Boolean = token(ctx).isNotBlank()

    fun parentName(ctx: Context): String = prefs(ctx).getString(KEY_PARENT_NAME, "") ?: ""
    fun setParentName(ctx: Context, name: String) = prefs(ctx).edit().putString(KEY_PARENT_NAME, name).apply()

    fun activeChildId(ctx: Context): Int = prefs(ctx).getInt(KEY_ACTIVE_CHILD, -1)
    fun setActiveChildId(ctx: Context, id: Int) = prefs(ctx).edit().putInt(KEY_ACTIVE_CHILD, id).apply()
    fun lastLocationSeen(ctx: Context, childId: Int): String =
        prefs(ctx).getString("last_location_seen_$childId", "") ?: ""

    fun setLastLocationSeen(ctx: Context, childId: Int, recordedAt: String) =
        prefs(ctx).edit().putString("last_location_seen_$childId", recordedAt).apply()

    fun warningFlag(ctx: Context, key: String): Boolean = prefs(ctx).getBoolean("warning_$key", false)
    fun setWarningFlag(ctx: Context, key: String, value: Boolean) = prefs(ctx).edit().putBoolean("warning_$key", value).apply()

    fun lastCallSeen(ctx: Context, childId: Int): String = prefs(ctx).getString("last_call_seen_$childId", "") ?: ""
    fun setLastCallSeen(ctx: Context, childId: Int, value: String) = prefs(ctx).edit().putString("last_call_seen_$childId", value).apply()

    fun notifyIncoming(ctx: Context): Boolean = prefs(ctx).getBoolean("notify_incoming", true)
    fun notifyOutgoing(ctx: Context): Boolean = prefs(ctx).getBoolean("notify_outgoing", true)
    fun notifyMissed(ctx: Context): Boolean = prefs(ctx).getBoolean("notify_missed", false)
    fun setCallNotifyPrefs(ctx: Context, incoming: Boolean, outgoing: Boolean, missed: Boolean) = prefs(ctx).edit()
        .putBoolean("notify_incoming", incoming).putBoolean("notify_outgoing", outgoing).putBoolean("notify_missed", missed).apply()
}
