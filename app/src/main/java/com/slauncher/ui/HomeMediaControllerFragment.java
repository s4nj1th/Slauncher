package com.slauncher.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.slauncher.media.MediaNotificationListener;

import java.util.List;

public class HomeMediaControllerFragment extends Fragment {
    private MediaSessionManager mediaSessionManager;
    private MediaController selectedController;
    private TextView titleView;
    private Button playPause, next, prev;

    private final BroadcastReceiver sessionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int count = intent.getIntExtra("count", 0);
            if (count > 0) {
                String pkg = intent.getStringExtra("pkg_0");
                selectControllerForPackage(pkg);
            } else {
                selectedController = null;
                updateUi();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaSessionManager = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
        getContext().registerReceiver(sessionsReceiver, new IntentFilter("com.slauncher.MEDIA_SESSIONS"));
    }

    @Override
    public View onCreateView(LayoutInflater li, ViewGroup vg, Bundle s) {
        View v = li.inflate(R.layout.fragment_home_media, vg, false);
        titleView = v.findViewById(R.id.media_title);
        playPause = v.findViewById(R.id.btn_play_pause);
        next = v.findViewById(R.id.btn_next);
        prev = v.findViewById(R.id.btn_prev);

        playPause.setOnClickListener(view -> {
            if (selectedController == null) return;
            int state = selectedController.getPlaybackState() != null ? selectedController.getPlaybackState().getState() : 0;
            if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                selectedController.getTransportControls().pause();
            } else {
                selectedController.getTransportControls().play();
            }
        });
        next.setOnClickListener(vv -> {
            if (selectedController != null) selectedController.getTransportControls().skipToNext();
        });
        prev.setOnClickListener(vv -> {
            if (selectedController != null) selectedController.getTransportControls().skipToPrevious();
        });

        updateUi();
        return v;
    }

    private void selectControllerForPackage(String pkg) {
        try {
            ComponentName listenerComp = new ComponentName(getContext(), MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
            selectedController = null;
            for (MediaController c : controllers) {
                if (c.getPackageName().equals(pkg)) {
                    selectedController = c;
                    break;
                }
            }
        } catch (SecurityException e) {
            selectedController = null;
        }
        updateUi();
    }

    private void updateUi() {
        if (selectedController != null && selectedController.getMetadata() != null) {
            CharSequence title = selectedController.getMetadata().getDescription().getTitle();
            titleView.setText(title != null ? title : selectedController.getPackageName());
            playPause.setEnabled(true); next.setEnabled(true); prev.setEnabled(true);
        } else {
            titleView.setText("No media");
            playPause.setEnabled(false); next.setEnabled(false); prev.setEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        getContext().unregisterReceiver(sessionsReceiver);
        super.onDestroy();
    }
}
