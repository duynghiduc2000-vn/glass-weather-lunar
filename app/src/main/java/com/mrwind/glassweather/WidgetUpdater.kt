package com.mrwind.glassweather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

object WidgetUpdater {

    private enum class WeatherEffectType {
        RAIN,
        SUN,
        FOG,
        NIGHT_STARS,
        NONE
    }

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

    fun buildPreviewEffectBitmap(
        showEffect: Boolean,
        condition: String,
        hourOfDay: Int,
        isLargeWidget: Boolean,
        useDarkStroke: Boolean
    ): Bitmap? {
        if (!showEffect) return null
        val effectType = detectEffectType(condition, hourOfDay)
        return createEffectBitmap(effectType, isLargeWidget, useDarkStroke)
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
        val conditionText = prefs.getString("condition", "Nhiều mây") ?: "Nhiều mây"
        val showSeconds = prefs.getBoolean("show_clock_seconds", false)
        val showWeatherEffect = prefs.getBoolean("show_weather_effect", true)
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

        views.setTextViewText(R.id.tv_condition, conditionText)
        views.setTextColor(R.id.tv_condition, secondaryTextColor)

        views.setTextViewText(R.id.tv_humidity_wind, prefs.getString("humidity_wind", "💧 78% • 💨 14km/h"))
        views.setTextColor(R.id.tv_humidity_wind, highlightTextColor)

        views.setTextViewText(R.id.tv_hourly, prefs.getString("hourly", ""))
        views.setTextColor(R.id.tv_hourly, hourlyTextColor)

        views.setTextViewText(R.id.tv_update_time, prefs.getString("last_update", "↻ --:--"))
        views.setTextViewText(R.id.tv_signature, signature)
        views.setTextColor(R.id.tv_signature, solarTextColor)

        val effectType = if (showWeatherEffect) {
            detectEffectType(conditionText, now.get(Calendar.HOUR_OF_DAY))
        } else {
            WeatherEffectType.NONE
        }
        val useDarkStroke = (widgetStyle == "glass_light") || (widgetStyle == "theme_color" && isLightThemeColor)
        val effectBitmap = createEffectBitmap(effectType, layoutId == R.layout.widget_4x4, useDarkStroke)
        views.setImageViewBitmap(R.id.iv_weather_effect, effectBitmap)

        return views
    }

    private fun detectEffectType(condition: String, hourOfDay: Int): WeatherEffectType {
        val lower = condition.lowercase(Locale.ROOT)
        val isNight = hourOfDay < 6 || hourOfDay >= 18

        return when {
            lower.contains("mưa") || lower.contains("rain") || lower.contains("dông") || lower.contains("giông") ->
                WeatherEffectType.RAIN
            lower.contains("sương") || lower.contains("mù") || lower.contains("fog") || lower.contains("mist") ->
                WeatherEffectType.FOG
            isNight && !lower.contains("nắng") ->
                WeatherEffectType.NIGHT_STARS
            lower.contains("nắng") || lower.contains("quang") || lower.contains("sun") || lower.contains("clear") ->
                WeatherEffectType.SUN
            else -> WeatherEffectType.NONE
        }
    }

    private fun createEffectBitmap(
        type: WeatherEffectType,
        isLargeWidget: Boolean,
        useDarkStroke: Boolean
    ): Bitmap? {
        if (type == WeatherEffectType.NONE) return null

        val width = if (isLargeWidget) 540 else 640
        val height = if (isLargeWidget) 540 else 320
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rng = Random(type.ordinal * 997 + width + height)

        when (type) {
            WeatherEffectType.RAIN -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (useDarkStroke) Color.parseColor("#380284C7") else Color.parseColor("#48BAE6FD")
                    strokeWidth = if (isLargeWidget) 3f else 2.4f
                    strokeCap = Paint.Cap.ROUND
                }
                val dropCount = if (isLargeWidget) 48 else 32
                repeat(dropCount) {
                    val x = rng.nextFloat() * width
                    val y = rng.nextFloat() * height
                    val len = rng.nextFloat() * 24f + 16f
                    canvas.drawLine(x, y, x - len * 0.28f, y + len, paint)
                }
            }

            WeatherEffectType.SUN -> {
                val cx = width * 0.84f
                val cy = height * 0.18f
                val radius = maxOf(width, height) * 0.48f
                val glowColor = if (useDarkStroke) Color.parseColor("#38F59E0B") else Color.parseColor("#42FDE047")
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx, cy, radius,
                        intArrayOf(glowColor, Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            WeatherEffectType.FOG -> {
                val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (useDarkStroke) Color.parseColor("#2464748B") else Color.parseColor("#28E2E8F0")
                    style = Paint.Style.FILL
                }
                val bands = if (isLargeWidget) 5 else 3
                for (i in 0 until bands) {
                    val y = height * (0.22f + i * 0.16f)
                    val rx = width * rng.nextFloat() * 0.25f + width * 0.22f
                    val ry = height * 0.06f
                    val x = width * rng.nextFloat()
                    canvas.drawOval(x - rx, y - ry, x + rx, y + ry, bandPaint)
                }
            }

            WeatherEffectType.NIGHT_STARS -> {
                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#C8FFFFFF")
                    style = Paint.Style.FILL
                }
                val starCount = if (isLargeWidget) 44 else 28
                repeat(starCount) {
                    val x = rng.nextFloat() * width
                    val y = rng.nextFloat() * height
                    val r = rng.nextFloat() * 2.2f + 1.1f
                    starPaint.alpha = rng.nextInt(90, 225)
                    canvas.drawCircle(x, y, r, starPaint)
                }
            }

            WeatherEffectType.NONE -> Unit
        }

        return bitmap
    }
}
