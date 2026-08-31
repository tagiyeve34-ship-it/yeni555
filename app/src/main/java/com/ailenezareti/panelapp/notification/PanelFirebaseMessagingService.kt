package com.ailenezareti.panelapp.notification

import com.ailenezareti.panelapp.Prefs
import com.ailenezareti.panelapp.api.ApiClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class PanelFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        registerTokenIfLoggedIn(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "Bildiriş"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        LocationNotificationManager.showFromPush(applicationContext, title, body, Random.nextInt(100_000, 999_999))
    }

    private fun registerTokenIfLoggedIn(token: String) {
        if (!Prefs.isLoggedIn(applicationContext)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.get(applicationContext).registerPushToken(
                    com.ailenezareti.panelapp.model.PushTokenRequest(token)
                )
            } catch (e: Exception) {
                // Sonrakı token yenilənməsində və ya sonrakı girişdə yenidən cəhd olunacaq
            }
        }
    }
}
