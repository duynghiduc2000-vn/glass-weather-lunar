package com.mrwind.glassweather

import android.content.Context
import androidx.work.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("GlassWeatherPrefs", Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", "11.2271") ?: "11.2271"
        val lon = prefs.getString("lon", "107.6671") ?: "107.6671"

        return try {
            val apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&hourly=temperature_2m&daily=temperature_2m_max,temperature_2m_min&timezone=auto"

            val response = URL(apiUrl).readText()
            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val hourly = json.getJSONObject("hourly")

            val temp = current.getDouble("temperature_2m").toInt()
            val humidity = current.getInt("relative_humidity_2m")
            val wind = current.getDouble("wind_speed_10m").toInt()
            val code = current.getInt("weather_code")
            val maxTemp = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
            val minTemp = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()

            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val temps = hourly.getJSONArray("temperature_2m")
            val hourlyStr = (1..4).joinToString("   ") { offset ->
                val h = (currentHour + offset) % 24
                val t = temps.getDouble(currentHour + offset).toInt()
                String.format("%02d:00 %d°", h, t)
            }

            val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            prefs.edit()
                .putString("temp", "$temp°C")
                .putString("min_max", "▲ $maxTemp°  ▼ $minTemp°")
                .putString("humidity_wind", "💧 $humidity% • 💨 ${wind}km/h")
                .putString("condition", mapWeatherCode(code))
                .putString("hourly", hourlyStr)
                .putString("last_update", "↻ $timeNow")
                .apply()

            WidgetUpdater.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun mapWeatherCode(code: Int): String = when (code) {
        0 -> "Trời quang"
        1, 2, 3 -> "Nhiều mây"
        45, 48 -> "Sương mù"
        51, 53, 55, 61, 63, 65 -> "Có mưa"
        80, 81, 82 -> "Mưa rào"
        95, 96, 99 -> "Dông bão"
        else -> "Mây rải rác"
    }

    companion object {
        fun schedulePeriodicUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WeatherWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "WeatherUpdate2Hours",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
