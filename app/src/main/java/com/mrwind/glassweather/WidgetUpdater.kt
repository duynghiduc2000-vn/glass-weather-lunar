package com.mrwind.glassweather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

object WidgetUpdater {

    fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val providers = listOf(
            Widget4x2Provider::class.java to R.layout.widget_4x2,
            Widget4x4Provider::class.java to R.layout.widget_4x4
        )

        for ((providerClass, layoutId) in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
            for (id in ids) {
                val views = buildRemoteViews(context, layoutId)
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun buildRemoteViews(context: Context, layoutId: Int): RemoteViews {
        val prefs = context.getSharedPreferences("GlassWeatherPrefs", Context.MODE_PRIVATE)
        val views = RemoteViews(context.packageName, layoutId)

        val themeColorHex = prefs.getString("theme_color", "#FF6D00") ?: "#FF6D00"
        val accentColor = Color.parseColor(themeColorHex)
        val widgetStyle = prefs.getString("widget_style", "glass_dark")
        val signature = prefs.getString("signature", "") ?: ""
        val locationName = prefs.getString("location_name", "Nghị Đức") ?: "Nghị Đức"

        when (widgetStyle) {
            "transparent" -> {
                views.setInt(R.id.widget_root, "setBackgroundColor", Color.TRANSPARENT)
            }
            "glass_light" -> {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_glass_light)
            }
            "theme_color" -> {
                val tintedBg = Color.argb(215, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                views.setInt(R.id.widget_root, "setBackgroundColor", tintedBg)
            }
            else -> {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_glass_dark)
            }
        }

        val now = Calendar.getInstance()
        val daysVi = arrayOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
        val dayOfWeek = daysVi[now.get(Calendar.DAY_OF_WEEK) - 1]
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now.time)

        views.setTextViewText(R.id.tv_solar_date, "$dayOfWeek • $dateStr")
        views.setTextViewText(R.id.tv_lunar_date, LunarCalendarHelper.getFullLunarString(now))
        views.setTextColor(R.id.tv_lunar_date, if (widgetStyle == "theme_color") Color.WHITE else accentColor)

        views.setTextViewText(R.id.tv_location, locationName)
        views.setTextViewText(R.id.tv_temp, "☁ ${prefs.getString("temp", "26°C")}")
        views.setTextViewText(R.id.tv_min_max, prefs.getString("min_max", "▲ 26°  ▼ 23°"))
        views.setTextViewText(R.id.tv_condition, prefs.getString("condition", "Nhiều mây"))
        views.setTextViewText(R.id.tv_humidity_wind, prefs.getString("humidity_wind", "💧 78% • 💨 14km/h"))
        views.setTextColor(R.id.tv_humidity_wind, if (widgetStyle == "theme_color") Color.WHITE else accentColor)
        views.setTextViewText(R.id.tv_hourly, prefs.getString("hourly", ""))
        views.setTextViewText(R.id.tv_update_time, prefs.getString("last_update", "↻ --:--"))
        views.setTextViewText(R.id.tv_signature, signature)

        return views
    }
}
