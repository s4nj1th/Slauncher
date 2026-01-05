package app.slauncher.helper.usageStats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log

/**
 * “…a diminutive Guardian who traveled backward through time…”
 *
 * Guards [EventLogWrapper] against Faulty unmatched close events (per
 * [the documentation](https://codeberg.org/fynngodau/usageDirect/wiki/Event-log-wrapper-scenarios))
 * by seeking backwards through time and scanning for the open event.
 */
class UnmatchedCloseEventGuardian(private val usageStatsManager: UsageStatsManager) {

    companion object {
        private const val SCAN_INTERVAL = 1000L * 60 * 60 * 24 
    }

    /**
     * @param event      Event to validate
     * @param queryStart Timestamp at which original query o
     * @return True if the event is valid, false otherwise
     */
    fun test(event: UsageEvents.Event, queryStart: Long): Boolean {
        val events = usageStatsManager.queryEvents(queryStart - SCAN_INTERVAL, queryStart)

        
        val e = UsageEvents.Event()

        
        var open = false 

        while (events.hasNextEvent()) {
            events.getNextEvent(e)

            if (e.eventType == UsageEvents.Event.DEVICE_STARTUP) {
                
                open = false
            }

            
            if (event.packageName == e.packageName) {
                when (e.eventType) {
                    
                    UsageEvents.Event.ACTIVITY_RESUMED, 4 -> {
                        open = true
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, 3 -> {
                        if (e.timeStamp != event.timeStamp) {
                            
                            open = false
                        }
                    }
                }
            }
        }

        val result = if (open) "True" else "Faulty"
        Log.d("Guardian", "Scanned for package ${event.packageName} and determined event to be $result")

        
        return open
    }
}
