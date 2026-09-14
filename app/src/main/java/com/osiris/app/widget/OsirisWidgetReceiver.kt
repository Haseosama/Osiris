package com.osiris.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The system-facing half of the widget — `AppWidgetManager` talks to this receiver (registered
 * in AndroidManifest.xml), which just hands off to the actual Compose content in [OsirisWidget]. */
class OsirisWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OsirisWidget()
}
