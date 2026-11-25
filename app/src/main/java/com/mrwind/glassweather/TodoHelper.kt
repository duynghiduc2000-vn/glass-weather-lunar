package com.mrwind.glassweather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class TodoItem(
    val time: String,
    val task: String
)

object TodoHelper {
    private const val PREFS_NAME = "GlassWeatherPrefs"
    private const val KEY_TODOS = "daily_todo_items"
    const val MAX_TODOS = 10

    fun getTodos(context: Context): List<TodoItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TODOS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<TodoItem>()
            for (i in 0 until minOf(arr.length(), MAX_TODOS)) {
                val obj = arr.optJSONObject(i) ?: continue
                val time = obj.optString("time", "").trim()
                val task = obj.optString("task", "").trim()
                if (task.isNotEmpty()) {
                    result.add(TodoItem(time = time, task = task))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTodos(context: Context, items: List<TodoItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        items.take(MAX_TODOS).forEach { item ->
            val cleanTask = item.task.trim()
            if (cleanTask.isNotEmpty()) {
                val obj = JSONObject()
                obj.put("time", item.time.trim())
                obj.put("task", cleanTask)
                arr.put(obj)
            }
        }
        prefs.edit().putString(KEY_TODOS, arr.toString()).apply()
    }

    /**
     * Trả về chuỗi hiển thị cho ô ngày lễ/việc cần làm tại thời điểm hiện tại.
     * Cứ mỗi 1 phút sẽ đổi sang mục tiếp theo để không chồng chéo.
     */
    fun getCurrentRotatingBannerText(context: Context, now: Calendar): String {
        val rotatingItems = mutableListOf<String>()

        val holiday = HolidayHelper.getTodayHolidayBanner(now)
        if (!holiday.isNullOrBlank()) {
            rotatingItems.add(holiday.trim())
        }

        val todos = getTodos(context)
        for (item in todos) {
            val formatted = if (item.time.isNotBlank()) {
                "⏰ ${item.time} • ${item.task}"
            } else {
                "• ${item.task}"
            }
            rotatingItems.add(formatted)
        }

        if (rotatingItems.isEmpty()) return ""

        val minuteBucket = System.currentTimeMillis() / 60_000L
        val index = ((minuteBucket % rotatingItems.size) + rotatingItems.size).toInt() % rotatingItems.size
        return rotatingItems[index]
    }
}
