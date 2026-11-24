package com.mrwind.glassweather

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

open class BaseWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateAllWidgets(context)
        WeatherWorker.schedulePeriodicUpdate(context)
    }
}

class Widget4x2Provider : BaseWeatherWidgetProvider()
class Widget4x4Provider : BaseWeatherWidgetProvider()
