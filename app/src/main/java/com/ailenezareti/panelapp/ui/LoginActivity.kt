package com.ailenezareti.panelapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ailenezareti.panelapp.Prefs
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.databinding.ActivityLoginBinding
import com.ailenezareti.panelapp.model.LoginRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Prefs.isLoggedIn(this)) {
            goToMain()
            return
        }

        binding.loginButton.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        if (email.isBlank() || password.isBlank()) {
            showError(getString(R.string.login_failed))
            return
        }

        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.get(this@LoginActivity).login(LoginRequest(email, password))
                runOnUiThread {
                    setLoading(false)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        Prefs.setToken(this@LoginActivity, body.token)
                        Prefs.setParentName(this@LoginActivity, body.parent.full_name)
                        registerFcmToken()
                        goToMain()
                    } else {
                        showError(getString(R.string.login_failed))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    showError(getString(R.string.network_error))
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loginButton.isEnabled = !loading
        binding.loginProgress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showError(msg: String) {
        binding.errorText.text = msg
        binding.errorText.visibility = android.view.View.VISIBLE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // Giriş uğurlu olan kimi cihazın mövcud FCM tokenini serverə bağlayır.
    // Firebase konfiqurasiyası (google-services.json) hələ əlavə olunmayıbsa,
    // FirebaseMessaging.getInstance() İSTİSNA ATIR — bu try-catch olmadan
    // tətbiq giriş elə bu anda çökürdü. İndi problemsiz keçilir, sadəcə
    // push bildirişi aktiv olmur (Firebase əlavə olunanda özü işə düşəcək).
    private fun registerFcmToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ApiClient.get(this@LoginActivity).registerPushToken(
                            com.ailenezareti.panelapp.model.PushTokenRequest(token)
                        )
                    } catch (e: Exception) { /* problem olsa da giriş prosesini bloklamasın */ }
                }
            }?.addOnFailureListener { /* Firebase hazır deyilsə səssizcə keç */ }
        } catch (e: Throwable) {
            // Firebase ümumiyyətlə işə düşməyibsə (google-services.json yoxdur,
            // "Default FirebaseApp is not initialized" və s.) buraya düşür.
            // Bu funksiya heç vaxt giriş prosesini poza bilməz.
        }
    }
}
