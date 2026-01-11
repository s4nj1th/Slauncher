package app.slauncher

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                try {
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    val root = content?.getChildAt(0)
                    if (root != null) {
                        FontManager.applyToView(root, activity)
                    }
                } catch (_: Exception) {}
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
