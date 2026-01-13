package app.slauncher.media;

import android.content.ComponentName;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.RequiresApi;

import java.util.List;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class MediaNotificationListener extends NotificationListenerService {
    private MediaSessionManager mediaSessionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        publishCurrentSessions();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        publishCurrentSessions();
    }

    private void publishCurrentSessions() {
        try {
            ComponentName me = new ComponentName(this, MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(me);
            Intent i = new Intent("com.slauncher.MEDIA_SESSIONS");
            i.putExtra("count", controllers.size());
            for (int idx = 0; idx < controllers.size(); idx++) {
                MediaController c = controllers.get(idx);
                i.putExtra("pkg_" + idx, c.getPackageName());
            }
            sendBroadcast(i);
        } catch (SecurityException e) {
            // listener not enabled
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        publishCurrentSessions();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        publishCurrentSessions();
    }
}
