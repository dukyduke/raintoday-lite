package net.chevillot.raintoday

import android.app.Application
import net.chevillot.raintoday.platform.Notifications
import net.chevillot.raintoday.work.AlertScheduler

class RainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        AlertScheduler.ensureScheduled(this)
    }
}
