package com.ailenezareti.panelapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ailenezareti.panelapp.Prefs
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.databinding.ActivityMainBinding
import com.ailenezareti.panelapp.model.Child
import com.ailenezareti.panelapp.notification.LocationNotificationManager
import com.ailenezareti.panelapp.notification.LocationUpdateChecker
import com.ailenezareti.panelapp.notification.LocationUpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var childAdapter: ChildChipAdapter
    var children: List<Child> = emptyList()
        private set

    private val foregroundHandler = Handler(Looper.getMainLooper())
    private val foregroundCheck = object : Runnable {
        override fun run() {
            lifecycleScope.launch(Dispatchers.IO) {
                LocationUpdateChecker.check(this@MainActivity, notifyOnChange = true)
            }
            foregroundHandler.postDelayed(this, 60_000L)
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        LocationNotificationManager.createChannel(this)
        requestNotificationPermissionIfNeeded()
        LocationUpdateScheduler.schedule(this)
        requestBatteryOptimizationExemptionIfNeeded()

        childAdapter = ChildChipAdapter { child -> onChildSelected(child) }
        binding.childRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.childRecycler.adapter = childAdapter

        binding.logoutButton.setOnClickListener {
            LocationUpdateScheduler.cancel(this)
            Prefs.clearToken(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            binding.topHeader.visibility = if (item.itemId == R.id.nav_location) android.view.View.GONE else android.view.View.VISIBLE
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_location -> LocationFragment()
                R.id.nav_calls -> CallsFragment()
                R.id.nav_zones -> ZonesFragment()
                R.id.nav_alerts -> AlertsFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        loadChildren()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (children.isNotEmpty()) {
            openLocationFromNotificationIfNeeded()
        }
    }

    override fun onStart() {
        super.onStart()
        foregroundHandler.removeCallbacks(foregroundCheck)
        foregroundHandler.post(foregroundCheck)
    }

    override fun onStop() {
        foregroundHandler.removeCallbacks(foregroundCheck)
        super.onStop()
    }

    fun activeChild(): Child? {
        val id = Prefs.activeChildId(this)
        return children.find { it.id == id } ?: children.firstOrNull()
    }

    private fun onChildSelected(child: Child) {
        Prefs.setActiveChildId(this, child.id)
        childAdapter.setActive(child.id)
        refreshCurrentFragment()
    }

    private fun refreshCurrentFragment() {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        (current as? Refreshable)?.refresh()
    }

    fun openLocationTab() {
        binding.bottomNav.selectedItemId = R.id.nav_location
    }

    private fun loadChildren() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.get(this@MainActivity).getChildren()
                runOnUiThread {
                    if (response.isSuccessful && response.body() != null) {
                        children = response.body()!!.children
                        if (children.isEmpty()) {
                            Toast.makeText(this@MainActivity, R.string.no_children, Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }

                        var activeId = Prefs.activeChildId(this@MainActivity)
                        if (children.none { it.id == activeId }) {
                            activeId = children.first().id
                            Prefs.setActiveChildId(this@MainActivity, activeId)
                        }
                        childAdapter.submit(children, activeId)

                        if (!openLocationFromNotificationIfNeeded()) {
                            if (savedFragmentIsEmpty()) {
                                supportFragmentManager.beginTransaction()
                                    .replace(R.id.fragmentContainer, HomeFragment())
                                    .commit()
                            } else {
                                refreshCurrentFragment()
                            }
                        }
                    } else if (response.code() == 401) {
                        LocationUpdateScheduler.cancel(this@MainActivity)
                        Prefs.clearToken(this@MainActivity)
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        finish()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.network_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openLocationFromNotificationIfNeeded(): Boolean {
        if (!intent.getBooleanExtra(LocationNotificationManager.EXTRA_OPEN_LOCATION, false)) return false

        val childId = intent.getIntExtra(LocationNotificationManager.EXTRA_CHILD_ID, -1)
        val lat = intent.getDoubleExtra(LocationNotificationManager.EXTRA_LATITUDE, Double.NaN)
        val lon = intent.getDoubleExtra(LocationNotificationManager.EXTRA_LONGITUDE, Double.NaN)
        val recordedAt = intent.getStringExtra(LocationNotificationManager.EXTRA_RECORDED_AT).orEmpty()

        if (children.none { it.id == childId } || lat.isNaN() || lon.isNaN()) return false

        Prefs.setActiveChildId(this, childId)
        childAdapter.setActive(childId)

        val fragment = LocationFragment.newInstance(lat, lon, recordedAt)
        binding.topHeader.visibility = android.view.View.GONE
        binding.bottomNav.menu.findItem(R.id.nav_location).isChecked = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        intent.removeExtra(LocationNotificationManager.EXTRA_OPEN_LOCATION)
        return true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Bildirişlərin (zəng/zona/batareya) VAXTINDA gəlməsi üçün — onsuz Android
    // arxa fon yoxlamasını (WorkManager) Doze rejimində saatlarla gecikdirə bilər.
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Bəzi cihaz istehsalçıları bu ekranı özəlləşdirib bloklaya bilər — sükutla keç
            }
        }
    }

    private fun savedFragmentIsEmpty(): Boolean =
        supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null
}
