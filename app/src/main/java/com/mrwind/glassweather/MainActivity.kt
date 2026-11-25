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
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etLocation: EditText
    private lateinit var etLat: EditText
    private lateinit var etLon: EditText
    private lateinit var etSignature: EditText
    private lateinit var swShowSeconds: SwitchMaterial
    private lateinit var swWeatherEffect: SwitchMaterial
    private lateinit var rgWidgetStyle: RadioGroup

    private lateinit var cardWidgetPreviewFrame: MaterialCardView
    private lateinit var previewWidgetRoot: RelativeLayout
    private lateinit var ivPreviewEffect: ImageView
    private lateinit var tvPreviewDate: TextView
    private lateinit var tvPreviewClock: TextView
    private lateinit var tvPreviewLunar: TextView
    private lateinit var tvPreviewTemp: TextView
    private lateinit var tvPreviewLocation: TextView
    private lateinit var tvPreviewCondition: TextView
    private lateinit var tvPreviewSignature: TextView
    private lateinit var btnPreviewSizeToggle: MaterialButton

    private var previewLargeFrame = false
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
        swWeatherEffect = findViewById(R.id.sw_weather_effect)
        rgWidgetStyle = findViewById(R.id.rg_widget_style)

        cardWidgetPreviewFrame = findViewById(R.id.card_widget_preview_frame)
        previewWidgetRoot = findViewById(R.id.preview_widget_root)
        ivPreviewEffect = findViewById(R.id.iv_preview_effect)
        tvPreviewDate = findViewById(R.id.tv_preview_date)
        tvPreviewClock = findViewById(R.id.tv_preview_clock)
        tvPreviewLunar = findViewById(R.id.tv_preview_lunar)
        tvPreviewTemp = findViewById(R.id.tv_preview_temp)
        tvPreviewLocation = findViewById(R.id.tv_preview_location)
        tvPreviewCondition = findViewById(R.id.tv_preview_condition)
        tvPreviewSignature = findViewById(R.id.tv_preview_signature)
        btnPreviewSizeToggle = findViewById(R.id.btn_preview_size_toggle)

        etLocation.setText(prefs.getString("location_name", "Nghị Đức"))
        etLat.setText(prefs.getString("lat", "11.2271"))
        etLon.setText(prefs.getString("lon", "107.6671"))
        etSignature.setText(prefs.getString("signature", ""))

        when (prefs.getString("widget_style", "glass_dark")) {
            "transparent" -> rgWidgetStyle.check(R.id.rb_transparent)
            "glass_light" -> rgWidgetStyle.check(R.id.rb_glass_light)
            "theme_color" -> rgWidgetStyle.check(R.id.rb_theme_color)
            else -> rgWidgetStyle.check(R.id.rb_glass_dark)
        }

        val savedSeconds = prefs.getBoolean("show_clock_seconds", false)
        setSwitchCheckedSafely(savedSeconds)

        val savedWeatherEffect = prefs.getBoolean("show_weather_effect", true)
        swWeatherEffect.isChecked = savedWeatherEffect
        swWeatherEffect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_weather_effect", isChecked).apply()
            WidgetUpdater.updateAllWidgets(this)
            refreshWidgetPreview(prefs)
        }

        swShowSeconds.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitchProgrammatically) return@setOnCheckedChangeListener

            if (isChecked) {
                setSwitchCheckedSafely(false)
                showModernBatteryWarningDialog(prefs)
            } else {
                prefs.edit().putBoolean("show_clock_seconds", false).apply()
                WidgetUpdater.updateAllWidgets(this)
                refreshWidgetPreview(prefs)
            }
        }

        rgWidgetStyle.setOnCheckedChangeListener { _, _ ->
            refreshWidgetPreview(prefs)
        }

        btnPreviewSizeToggle.setOnClickListener {
            previewLargeFrame = !previewLargeFrame
            btnPreviewSizeToggle.text = if (previewLargeFrame) "Khung 4x4" else "Khung 4x2"
            cardWidgetPreviewFrame.layoutParams = cardWidgetPreviewFrame.layoutParams.apply {
                height = (if (previewLargeFrame) 230 else 156) * resources.displayMetrics.density.toInt()
            }
            refreshWidgetPreview(prefs)
        }

        val previewWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refreshWidgetPreview(prefs)
            }
        }
        etLocation.addTextChangedListener(previewWatcher)
        etSignature.addTextChangedListener(previewWatcher)

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
            // 9 màu sáng mới
            R.id.color_sky to "#38BDF8",
            R.id.color_aqua to "#2DD4BF",
            R.id.color_lime to "#4ADE80",
            R.id.color_sun to "#FACC15",
            R.id.color_peach to "#FB923C",
            R.id.color_rose to "#F472B6",
            R.id.color_lilac to "#C084FC",
            R.id.color_ice to "#67E8F9",
            R.id.color_pearl to "#E2E8F0",
            // 9 màu gốc
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
                refreshWidgetPreview(prefs)
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
            val selectedStyle = getSelectedWidgetStyle()

            prefs.edit()
                .putString("location_name", etLocation.text.toString())
                .putString("lat", etLat.text.toString())
                .putString("lon", etLon.text.toString())
                .putString("signature", etSignature.text.toString())
                .putString("widget_style", selectedStyle)
                .putBoolean("show_clock_seconds", swShowSeconds.isChecked)
                .putBoolean("show_weather_effect", swWeatherEffect.isChecked)
                .apply()

            WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
            WidgetUpdater.updateAllWidgets(this)
            refreshWidgetPreview(prefs)
            Toast.makeText(this, "Đã lưu & cập nhật Widget!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_pin_widget).setOnClickListener {
            val providerClass = when (findViewById<RadioGroup>(R.id.rg_pin_size).checkedRadioButtonId) {
                R.id.rb_size_4x4 -> Widget4x4Provider::class.java
                else -> Widget4x2Provider::class.java
            }
            pinWidgetToHomeScreen(providerClass)
        }

        refreshWidgetPreview(prefs)
        checkAndShowAdminBirthdayDialog(prefs)

        if (hasLocationPermission()) {
            fetchGpsAndLocationName(showToast = false, saveAndRefreshWidget = true)
        }
    }

    private fun getSelectedWidgetStyle(): String {
        return when (rgWidgetStyle.checkedRadioButtonId) {
            R.id.rb_transparent -> "transparent"
            R.id.rb_glass_light -> "glass_light"
            R.id.rb_theme_color -> "theme_color"
            else -> "glass_dark"
        }
    }

    private fun refreshWidgetPreview(prefs: android.content.SharedPreferences) {
        val themeColorHex = prefs.getString("theme_color", "#FF6D00") ?: "#FF6D00"
        val accentColor = try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            Color.parseColor("#FF6D00")
        }
        val widgetStyle = getSelectedWidgetStyle()
        val isLightThemeColor = ColorUtils.calculateLuminance(accentColor) > 0.45
        val themePrimaryText = if (isLightThemeColor) Color.parseColor("#111827") else Color.WHITE
        val themeSecondaryText = if (isLightThemeColor) Color.parseColor("#CC111827") else Color.parseColor("#CCFFFFFF")
        val themeHighlightText = if (isLightThemeColor) Color.parseColor("#0F172A") else Color.WHITE

        when (widgetStyle) {
            "transparent" -> previewWidgetRoot.setBackgroundColor(Color.TRANSPARENT)
            "glass_light" -> previewWidgetRoot.setBackgroundResource(R.drawable.bg_widget_glass_light)
            "theme_color" -> {
                val tintedBg = Color.argb(215, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                previewWidgetRoot.setBackgroundColor(tintedBg)
            }
            else -> previewWidgetRoot.setBackgroundResource(R.drawable.bg_widget_glass_dark)
        }

        val primaryColor = when (widgetStyle) {
            "glass_light" -> Color.parseColor("#111827")
            "theme_color" -> themePrimaryText
            else -> Color.WHITE
        }
        val secondaryColor = when (widgetStyle) {
            "glass_light" -> Color.parseColor("#CC111827")
            "theme_color" -> themeSecondaryText
            else -> Color.parseColor("#CCFFFFFF")
        }
        val highlightColor = when (widgetStyle) {
            "theme_color" -> themeHighlightText
            else -> accentColor
        }

        val now = Calendar.getInstance()
        val daysVi = arrayOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
        val dayOfWeek = daysVi[now.get(Calendar.DAY_OF_WEEK) - 1]
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now.time)
        val pattern = if (swShowSeconds.isChecked) "HH:mm:ss" else "HH:mm"
        val clockStr = SimpleDateFormat(pattern, Locale.getDefault()).format(now.time)

        tvPreviewDate.text = "$dayOfWeek • $dateStr"
        tvPreviewDate.setTextColor(primaryColor)
        tvPreviewClock.text = clockStr
        tvPreviewClock.setTextColor(primaryColor)

        tvPreviewLunar.text = LunarCalendarHelper.getFullLunarString(now)
        tvPreviewLunar.setTextColor(highlightColor)

        tvPreviewTemp.text = "☁ ${prefs.getString("temp", "24°C")}"
        tvPreviewTemp.setTextColor(primaryColor)

        val locText = etLocation.text.toString().ifBlank {
            prefs.getString("location_name", "Nghị Đức") ?: "Nghị Đức"
        }
        tvPreviewLocation.text = locText
        tvPreviewLocation.setTextColor(primaryColor)

        val condText = prefs.getString("condition", "Nhiều mây") ?: "Nhiều mây"
        tvPreviewCondition.text = condText
        tvPreviewCondition.setTextColor(secondaryColor)

        val sigText = etSignature.text.toString()
        tvPreviewSignature.text = sigText
        tvPreviewSignature.setTextColor(primaryColor)

        val useDarkStroke = (widgetStyle == "glass_light") || (widgetStyle == "theme_color" && isLightThemeColor)
        ivPreviewEffect.setImageBitmap(
            WidgetUpdater.buildPreviewEffectBitmap(
                showEffect = swWeatherEffect.isChecked,
                condition = condText,
                hourOfDay = now.get(Calendar.HOUR_OF_DAY),
                isLargeWidget = previewLargeFrame,
                useDarkStroke = useDarkStroke
            )
        )
    }

    private fun checkAndShowAdminBirthdayDialog(prefs: android.content.SharedPreferences) {
        val now = Calendar.getInstance()
        val isDec31 = now.get(Calendar.MONTH) == Calendar.DECEMBER &&
                now.get(Calendar.DAY_OF_MONTH) == 31
        if (!isDec31) return

        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val lastShownDate = prefs.getString("last_birthday_popup_date", "")
        if (lastShownDate == todayKey) return

        prefs.edit().putString("last_birthday_popup_date", todayKey).apply()

        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_birthday, null)
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

        val btnOk = dialogView.findViewById<MaterialButton>(R.id.btn_bday_ok)
        btnOk.setBackgroundColor(accentColor)
        btnOk.setTextColor(
            if (ColorUtils.calculateLuminance(accentColor) > 0.45) {
                Color.parseColor("#111827")
            } else {
                Color.WHITE
            }
        )
        btnOk.setOnClickListener { dialog.dismiss() }

        dialog.show()
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
            refreshWidgetPreview(prefs)
            dialog.dismiss()
        }

        btnYes.setOnClickListener {
            setSwitchCheckedSafely(true)
            prefs.edit().putBoolean("show_clock_seconds", true).apply()
            WidgetUpdater.updateAllWidgets(this)
            refreshWidgetPreview(prefs)
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
                        refreshWidgetPreview(prefs)
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
