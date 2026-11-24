package com.mrwind.glassweather

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etLocation: EditText
    private lateinit var etLat: EditText
    private lateinit var etLon: EditText
    private lateinit var etSignature: EditText
    private lateinit var swShowSeconds: SwitchMaterial
    private var isUpdatingSwitchProgrammatically = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        val prefs = getSharedPreferences("GlassWeatherPrefs", Context.MODE_PRIVATE)
        applyAppTheme(prefs.getInt("app_theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val rootView = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        WeatherWorker.schedulePeriodicUpdate(this)

        etLocation = findViewById(R.id.et_location)
        etLat = findViewById(R.id.et_lat)
        etLon = findViewById(R.id.et_lon)
        etSignature = findViewById(R.id.et_signature)
        swShowSeconds = findViewById(R.id.sw_show_seconds)

        etLocation.setText(prefs.getString("location_name", "Nghị Đức"))
        etLat.setText(prefs.getString("lat", "11.2271"))
        etLon.setText(prefs.getString("lon", "107.6671"))
        etSignature.setText(prefs.getString("signature", ""))

        val savedSeconds = prefs.getBoolean("show_clock_seconds", false)
        setSwitchCheckedSafely(savedSeconds)

        swShowSeconds.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitchProgrammatically) return@setOnCheckedChangeListener

            if (isChecked) {
                setSwitchCheckedSafely(false)
                showModernBatteryWarningDialog(prefs)
            } else {
                prefs.edit().putBoolean("show_clock_seconds", false).apply()
                WidgetUpdater.updateAllWidgets(this)
            }
        }

        findViewById<Button>(R.id.btn_theme_system).setOnClickListener {
            saveAndApplyTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        findViewById<Button>(R.id.btn_theme_light).setOnClickListener {
            saveAndApplyTheme(AppCompatDelegate.MODE_NIGHT_NO)
        }
        findViewById<Button>(R.id.btn_theme_dark).setOnClickListener {
            saveAndApplyTheme(AppCompatDelegate.MODE_NIGHT_YES)
        }

        val colorMap = mapOf(
            R.id.color_ocean to "#0288D1",
            R.id.color_emerald to "#00897B",
            R.id.color_sunset to "#EF6C00",
            R.id.color_lavender to "#7E57C2",
            R.id.color_coral to "#D81B60",
            R.id.color_amber to "#D97706",
            R.id.color_ruby to "#D32F2F",
            R.id.color_mint to "#00796B",
            R.id.color_graphite to "#455A64"
        )
        colorMap.forEach { (viewId, hex) ->
            findViewById<LinearLayout>(viewId)?.setOnClickListener {
                prefs.edit().putString("theme_color", hex).apply()
                WidgetUpdater.updateAllWidgets(this)
                Toast.makeText(this, "Đã đổi màu chủ đề", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_get_gps).setOnClickListener {
            if (!hasLocationPermission()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    100
                )
                return@setOnClickListener
            }
            fetchGpsAndLocationName(showToast = true, saveAndRefreshWidget = false)
        }

        findViewById<Button>(R.id.btn_save_update).setOnClickListener {
            val selectedStyle = when (findViewById<RadioGroup>(R.id.rg_widget_style).checkedRadioButtonId) {
                R.id.rb_transparent -> "transparent"
                R.id.rb_glass_light -> "glass_light"
                R.id.rb_theme_color -> "theme_color"
                else -> "glass_dark"
            }

            prefs.edit()
                .putString("location_name", etLocation.text.toString())
                .putString("lat", etLat.text.toString())
                .putString("lon", etLon.text.toString())
                .putString("signature", etSignature.text.toString())
                .putString("widget_style", selectedStyle)
                .putBoolean("show_clock_seconds", swShowSeconds.isChecked)
                .apply()

            WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
            WidgetUpdater.updateAllWidgets(this)
            Toast.makeText(this, "Đã lưu & cập nhật Widget!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_pin_widget).setOnClickListener {
            val providerClass = when (findViewById<RadioGroup>(R.id.rg_pin_size).checkedRadioButtonId) {
                R.id.rb_size_4x4 -> Widget4x4Provider::class.java
                else -> Widget4x2Provider::class.java
            }
            pinWidgetToHomeScreen(providerClass)
        }

        if (hasLocationPermission()) {
            fetchGpsAndLocationName(showToast = false, saveAndRefreshWidget = true)
        }
    }

    private fun showModernBatteryWarningDialog(prefs: android.content.SharedPreferences) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_battery_warning, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val themeHex = prefs.getString("theme_color", "#FF6D00") ?: "#FF6D00"
        val accentColor = try {
            Color.parseColor(themeHex)
        } catch (_: Exception) {
            Color.parseColor("#FF6D00")
        }

        val btnNo = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_no)
        val btnYes = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_yes)

        btnYes.setBackgroundColor(accentColor)
        val textOnAccent = if (ColorUtils.calculateLuminance(accentColor) > 0.45) {
            Color.parseColor("#111827")
        } else {
            Color.WHITE
        }
        btnYes.setTextColor(textOnAccent)
        btnNo.strokeColor = android.content.res.ColorStateList.valueOf(accentColor)

        btnNo.setOnClickListener {
            setSwitchCheckedSafely(false)
            prefs.edit().putBoolean("show_clock_seconds", false).apply()
            dialog.dismiss()
        }

        btnYes.setOnClickListener {
            setSwitchCheckedSafely(true)
            prefs.edit().putBoolean("show_clock_seconds", true).apply()
            WidgetUpdater.updateAllWidgets(this)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setSwitchCheckedSafely(checked: Boolean) {
        isUpdatingSwitchProgrammatically = true
        swShowSeconds.isChecked = checked
        isUpdatingSwitchProgrammatically = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchGpsAndLocationName(showToast = true, saveAndRefreshWidget = false)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun fetchGpsAndLocationName(showToast: Boolean, saveAndRefreshWidget: Boolean) {
        if (!hasLocationPermission()) return

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val latStr = String.format(Locale.US, "%.4f", loc.latitude)
                val lonStr = String.format(Locale.US, "%.4f", loc.longitude)
                etLat.setText(latStr)
                etLon.setText(lonStr)

                resolveAddressName(loc.latitude, loc.longitude) { resolvedName ->
                    val finalName = if (!resolvedName.isNullOrBlank()) {
                        resolvedName
                    } else {
                        etLocation.text.toString().ifBlank { "Vị trí hiện tại" }
                    }
                    etLocation.setText(finalName)

                    if (saveAndRefreshWidget) {
                        val prefs = getSharedPreferences("GlassWeatherPrefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("location_name", finalName)
                            .putString("lat", latStr)
                            .putString("lon", lonStr)
                            .apply()
                        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
                        WidgetUpdater.updateAllWidgets(this)
                    }

                    if (showToast) {
                        Toast.makeText(this, "Đã lấy vị trí: $finalName", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (showToast) {
                Toast.makeText(this, "Chưa lấy được tọa độ GPS, hãy bật định vị và thử lại.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveAddressName(lat: Double, lon: Double, onResult: (String?) -> Unit) {
        Thread {
            try {
                val geocoder = Geocoder(this, Locale("vi", "VN"))
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val address = addresses?.firstOrNull()
                val placeName = address?.subAdminArea
                    ?: address?.locality
                    ?: address?.adminArea
                    ?: address?.featureName
                runOnUiThread { onResult(placeName) }
            } catch (_: Exception) {
                runOnUiThread { onResult(null) }
            }
        }.start()
    }

    private fun pinWidgetToHomeScreen(providerClass: Class<*>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = getSystemService(AppWidgetManager::class.java)
            val myProvider = ComponentName(this, providerClass)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
            } else {
                Toast.makeText(
                    this,
                    "Launcher hiện tại không hỗ trợ ghim nhanh, hãy giữ màn hình chính để thêm Widget.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveAndApplyTheme(mode: Int) {
        getSharedPreferences("GlassWeatherPrefs", Context.MODE_PRIVATE)
            .edit().putInt("app_theme_mode", mode).apply()
        applyAppTheme(mode)
    }

    private fun applyAppTheme(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
