package com.example.glassweather

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("GlassWeatherPrefs", Context.MODE_PRIVATE)
        applyAppTheme(prefs.getInt("app_theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WeatherWorker.schedulePeriodicUpdate(this)

        val etLocation = findViewById<EditText>(R.id.et_location)
        val etLat = findViewById<EditText>(R.id.et_lat)
        val etLon = findViewById<EditText>(R.id.et_lon)
        val etSignature = findViewById<EditText>(R.id.et_signature)

        etLocation.setText(prefs.getString("location_name", "Nghị Đức"))
        etLat.setText(prefs.getString("lat", "11.2271"))
        etLon.setText(prefs.getString("lon", "107.6671"))
        etSignature.setText(prefs.getString("signature", ""))

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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
                return@setOnClickListener
            }
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    etLat.setText(loc.latitude.toString())
                    etLon.setText(loc.longitude.toString())
                }
            }
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
                .apply()

            WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
            WidgetUpdater.updateAllWidgets(this)
            Toast.makeText(this, "Đã lưu & cập nhật Widget!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_pin_widget).setOnClickListener {
            val providerClass = when (findViewById<RadioGroup>(R.id.rg_pin_size).checkedRadioButtonId) {
                R.id.rb_size_2x2 -> Widget2x2Provider::class.java
                R.id.rb_size_4x4 -> Widget4x4Provider::class.java
                else -> Widget4x2Provider::class.java
            }
            pinWidgetToHomeScreen(providerClass)
        }
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
