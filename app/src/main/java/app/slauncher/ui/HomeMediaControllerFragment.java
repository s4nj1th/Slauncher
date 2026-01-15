package app.slauncher.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import app.slauncher.media.MediaNotificationListener;
import app.slauncher.R;

import java.util.ArrayList;
import java.util.List;

public class HomeMediaControllerFragment extends Fragment {
    private MediaSessionManager mediaSessionManager;
    private MediaController selectedController;
    private TextView titleView;
    private TextView artistView;
    private ImageButton playPause;
    private android.view.View next, prev;
    private MediaController.Callback controllerCallback;
    private SharedPreferences prefs;
    private String selectedPkg;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private final BroadcastReceiver sessionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int count = intent.getIntExtra("count", 0);
            if (count > 0) {
                if (selectedPkg == null) {
                    String pkg = intent.getStringExtra("pkg_0");
                    selectControllerForPackage(pkg);
                } else {
                    selectControllerForPackage(selectedPkg);
                }
            } else {
                selectedController = null;
                updateUi();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaSessionManager = (MediaSessionManager) requireContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
        prefs = requireContext().getSharedPreferences("home_media", Context.MODE_PRIVATE);
        selectedPkg = prefs.getString("selected_pkg", null);
        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if ("enabled".equals(key)) {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> updateUi());
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter filter = new IntentFilter("com.slauncher.MEDIA_SESSIONS");
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            // use the 5-arg overload to ensure flags are honored at runtime
            requireContext().registerReceiver(sessionsReceiver, filter, null, null, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(sessionsReceiver, filter);
        }
    }

    @Override
    public View onCreateView(LayoutInflater li, ViewGroup vg, Bundle s) {
        View v = li.inflate(R.layout.fragment_home_media, vg, false);
        titleView = v.findViewById(R.id.media_title);
        artistView = v.findViewById(R.id.media_artist);
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
            updatePlayPauseIcon();
        });
        next.setOnClickListener(vv -> {
            if (selectedController != null) selectedController.getTransportControls().skipToNext();
        });
        prev.setOnClickListener(vv -> {
            if (selectedController != null) selectedController.getTransportControls().skipToPrevious();
        });

        // chooser and enable-listener controls moved to Settings screen.

        updateUi();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (selectedPkg != null) selectControllerForPackage(selectedPkg);
        // Refresh UI to respect latest settings (show/hide when user toggles media widget)
        updateUi();
    }

    private void showChooserDialog() {
        try {
            ComponentName listenerComp = new ComponentName(requireContext(), MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
            if (controllers == null || controllers.isEmpty()) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("No active media")
                        .setMessage("No active media sessions found. Start playback in an app first.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            final List<String> labels = new ArrayList<>();
            final List<String> pkgs = new ArrayList<>();
            PackageManager pm = requireContext().getPackageManager();
            for (MediaController c : controllers) {
                String pkg = c.getPackageName();
                String label = pkg;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    CharSequence appLabel = pm.getApplicationLabel(ai);
                    if (appLabel != null) label = appLabel.toString();
                } catch (PackageManager.NameNotFoundException ignored) {
                }
                labels.add(label);
                pkgs.add(pkg);
            }

            CharSequence[] items = labels.toArray(new CharSequence[0]);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Select media app")
                    .setItems(items, (dialog, which) -> {
                        String pkg = pkgs.get(which);
                        prefs.edit().putString("selected_pkg", pkg).apply();
                        selectedPkg = pkg;
                        selectControllerForPackage(pkg);
                    })
                    .show();
        } catch (SecurityException e) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Permission required")
                    .setMessage("Notification listener permission is required. Please enable it in settings.")
                    .setPositiveButton("Open Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void ensureControllerCallback() {
        if (controllerCallback != null) return;
        controllerCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(android.media.session.PlaybackState state) {
                requireActivity().runOnUiThread(() -> updatePlayPauseIcon());
            }

            @Override
            public void onMetadataChanged(android.media.MediaMetadata metadata) {
                requireActivity().runOnUiThread(() -> updateUi());
            }
        };
    }

    private void selectControllerForPackage(String pkg) {
        try {
            ComponentName listenerComp = new ComponentName(requireContext(), MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
            // unregister previous callback
            if (selectedController != null && controllerCallback != null) {
                try { selectedController.unregisterCallback(controllerCallback); } catch (Exception ignored) {}
            }
            selectedController = null;
            if (controllers != null) {
                for (MediaController c : controllers) {
                    if (c.getPackageName().equals(pkg)) {
                        selectedController = c;
                        break;
                    }
                }
            }
            if (selectedController != null) {
                ensureControllerCallback();
                try { selectedController.registerCallback(controllerCallback); } catch (Exception ignored) {}
            }
        } catch (SecurityException e) {
            selectedController = null;
        }
        updateUi();
    }

    private void updateUi() {
        if (titleView == null) return;
        // respect user's setting for enabling the media widget
        boolean enabled = true;
        try {
            enabled = requireContext().getSharedPreferences("home_media", Context.MODE_PRIVATE).getBoolean("enabled", true);
        } catch (Exception ignored) { }

        View root = getView();
        if (root != null) {
            root.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }

        if (!enabled) {
            // when disabled, make sure controls are inactive
            titleView.setText("No media");
            if (artistView != null) artistView.setText("");
            playPause.setEnabled(false);
            next.setEnabled(false);
            prev.setEnabled(false);
            return;
        }

        // Widget is enabled; show placeholder if no active sessions or show metadata when available
        boolean hasControllers = false;
        try {
            ComponentName listenerComp = new ComponentName(requireContext(), MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
            if (controllers != null && !controllers.isEmpty()) hasControllers = true;
        } catch (SecurityException e) {
            hasControllers = false;
        }

        if (hasControllers && selectedController != null && selectedController.getMetadata() != null) {
            android.media.MediaMetadata md = selectedController.getMetadata();
            CharSequence title = md.getDescription().getTitle();

            // Use description.subtitle if it already contains both artist and album (e.g. "Artist - Album").
            String info = null;
            CharSequence subtitle = md.getDescription().getSubtitle();
            if (subtitle != null && subtitle.length() > 0) {
                String sub = subtitle.toString().trim();
                if (sub.contains(" - ")) {
                    info = sub;
                } else {
                    // treat subtitle as artist candidate
                    String artistStr = sub;
                    String albumStr = null;
                    try { albumStr = md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM); } catch (Exception ignored) { }
                    if (albumStr != null && !albumStr.isEmpty()) info = artistStr + " - " + albumStr.trim(); else info = artistStr;
                }
            } else {
                // no subtitle: try artist + album
                String artistStr = null;
                try { artistStr = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST); } catch (Exception ignored) { }
                String albumStr = null;
                try { albumStr = md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM); } catch (Exception ignored) { }
                if (artistStr != null && !artistStr.isEmpty()) {
                    if (albumStr != null && !albumStr.isEmpty()) info = artistStr.trim() + " - " + albumStr.trim(); else info = artistStr.trim();
                } else if (albumStr != null && !albumStr.isEmpty()) {
                    info = albumStr.trim();
                } else {
                    info = "";
                }
            }

            titleView.setText(title != null ? title : selectedController.getPackageName());
            if (artistView != null) artistView.setText(info);
            playPause.setEnabled(true);
            next.setEnabled(true);
            prev.setEnabled(true);
            updatePlayPauseIcon();
        } else {
            titleView.setText("No media");
            if (artistView != null) artistView.setText("");
            // enable controls so user can attempt play, but keep them safe (controllers may be null)
            playPause.setEnabled(selectedController != null);
            next.setEnabled(selectedController != null);
            prev.setEnabled(selectedController != null);
            updatePlayPauseIcon();
        }
    }

    private void updatePlayPauseIcon() {
        if (playPause == null) return;
        int state = selectedController != null && selectedController.getPlaybackState() != null
                ? selectedController.getPlaybackState().getState() : 0;
        if (state == android.media.session.PlaybackState.STATE_PLAYING) {
            playPause.setImageResource(R.drawable.ic_pause);
        } else {
            playPause.setImageResource(R.drawable.ic_play_arrow);
        }
    }

    @Override
    public void onDestroy() {
        requireContext().unregisterReceiver(sessionsReceiver);
        if (prefs != null && prefListener != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(prefListener); } catch (Exception ignored) {}
        }
        if (selectedController != null && controllerCallback != null) {
            try { selectedController.unregisterCallback(controllerCallback); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
