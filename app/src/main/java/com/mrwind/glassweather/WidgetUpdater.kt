package com.mrwind.glassweather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
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
        val accentColor = try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            Color.parseColor("#FF6D00")
        }
        val widgetStyle = prefs.getString("widget_style", "glass_dark")
        val signature = prefs.getString("signature", "") ?: ""
        val locationName = prefs.getString("location_name", "Nghị Đức") ?: "Nghị Đức"
        val showSeconds = prefs.getBoolean("show_clock_seconds", false)
        val clockFormat = if (showSeconds) "HH:mm:ss" else "HH:mm"

        val isLightThemeColor = ColorUtils.calculateLuminance(accentColor) > 0.45
        val themePrimaryText = if (isLightThemeColor) Color.parseColor("#111827") else Color.WHITE
        val themeSecondaryText = if (isLightThemeColor) Color.parseColor("#CC111827") else Color.parseColor("#CCFFFFFF")
        val themeHourlyText = if (isLightThemeColor) Color.parseColor("#E6111827") else Color.parseColor("#E6FFFFFF")
        val themeHighlightText = if (isLightThemeColor) Color.parseColor("#0F172A") else Color.WHITE

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

        val clockTextColor = when (widgetStyle) {
            "glass_light" -> Color.parseColor("#111827")
            "theme_color" -> themePrimaryText
            else -> Color.WHITE
        }
        views.setCharSequence(R.id.tc_clock, "setFormat12Hour", clockFormat)
        views.setCharSequence(R.id.tc_clock, "setFormat24Hour", clockFormat)
        views.setTextColor(R.id.tc_clock, clockTextColor)

        val solarTextColor = when (widgetStyle) {
            "glass_light" -> Color.parseColor("#111827")
            "theme_color" -> themePrimaryText
            else -> Color.WHITE
        }
        val secondaryTextColor = when (widgetStyle) {
            "glass_light" -> Color.parseColor("#CC111827")
            "theme_color" -> themeSecondaryText
            else -> Color.parseColor("#CCFFFFFF")
        }
        val hourlyTextColor = when (widgetStyle) {
            "glass_light" -> Color.parseColor("#E6111827")
            "theme_color" -> themeHourlyText
            else -> Color.parseColor("#E6FFFFFF")
        }
        val highlightTextColor = when (widgetStyle) {
            "theme_color" -> themeHighlightText
            else -> accentColor
        }

        val now = Calendar.getInstance()
        val daysVi = arrayOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
        val dayOfWeek = daysVi[now.get(Calendar.DAY_OF_WEEK) - 1]
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now.time)

        views.setTextViewText(R.id.tv_solar_date, "$dayOfWeek • $dateStr")
        views.setTextColor(R.id.tv_solar_date, solarTextColor)

        views.setTextViewText(R.id.tv_lunar_date, LunarCalendarHelper.getFullLunarString(now))
        views.setTextColor(R.id.tv_lunar_date, highlightTextColor)

        views.setTextViewText(R.id.tv_location, locationName)
        views.setTextColor(R.id.tv_location, solarTextColor)

        views.setTextViewText(R.id.tv_temp, "☁ ${prefs.getString("temp", "26°C")}")
        views.setTextColor(R.id.tv_temp, solarTextColor)

        views.setTextViewText(R.id.tv_min_max, prefs.getString("min_max", "▲ 26°  ▼ 23°"))
        views.setTextColor(R.id.tv_min_max, secondaryTextColor)

        views.setTextViewText(R.id.tv_condition, prefs.getString("condition", "Nhiều mây"))
        views.setTextColor(R.id.tv_condition, secondaryTextColor)

        views.setTextViewText(R.id.tv_humidity_wind, prefs.getString("humidity_wind", "💧 78% • 💨 14km/h"))
        views.setTextColor(R.id.tv_humidity_wind, highlightTextColor)

        views.setTextViewText(R.id.tv_hourly, prefs.getString("hourly", ""))
        views.setTextColor(R.id.tv_hourly, hourlyTextColor)

        views.setTextViewText(R.id.tv_update_time, prefs.getString("last_update", "↻ --:--"))
        views.setTextViewText(R.id.tv_signature, signature)
        views.setTextColor(R.id.tv_signature, solarTextColor)

        return views
    }
}
