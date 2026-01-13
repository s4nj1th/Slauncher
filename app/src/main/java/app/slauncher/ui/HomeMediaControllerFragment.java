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
import android.widget.Button;
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
    private Button playPause, next, prev, chooseApp, enableListener;
    private SharedPreferences prefs;
    private String selectedPkg;

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
        playPause = v.findViewById(R.id.btn_play_pause);
        next = v.findViewById(R.id.btn_next);
        prev = v.findViewById(R.id.btn_prev);
        chooseApp = v.findViewById(R.id.btn_choose_app);
        enableListener = v.findViewById(R.id.btn_enable_listener);

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

        chooseApp.setOnClickListener(vv -> showChooserDialog());

        enableListener.setOnClickListener(vv -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        updateUi();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (selectedPkg != null) selectControllerForPackage(selectedPkg);
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

    private void selectControllerForPackage(String pkg) {
        try {
            ComponentName listenerComp = new ComponentName(requireContext(), MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
            selectedController = null;
            if (controllers != null) {
                for (MediaController c : controllers) {
                    if (c.getPackageName().equals(pkg)) {
                        selectedController = c;
                        break;
                    }
                }
            }
        } catch (SecurityException e) {
            selectedController = null;
        }
        updateUi();
    }

    private void updateUi() {
        if (titleView == null) return;
        boolean visible = false;
        try {
            ComponentName listenerComp = new ComponentName(requireContext(), MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
            if (controllers != null && !controllers.isEmpty()) visible = true;
        } catch (SecurityException e) {
            visible = false;
        }

        View root = getView();
        if (root != null) {
            root.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        if (visible && selectedController != null && selectedController.getMetadata() != null) {
            CharSequence title = selectedController.getMetadata().getDescription().getTitle();
            titleView.setText(title != null ? title : selectedController.getPackageName());
            playPause.setEnabled(true);
            next.setEnabled(true);
            prev.setEnabled(true);
        } else if (visible) {
            titleView.setText("No media");
            playPause.setEnabled(true);
            next.setEnabled(true);
            prev.setEnabled(true);
        } else {
            // hidden: ensure controls disabled
            titleView.setText("No media");
            playPause.setEnabled(false);
            next.setEnabled(false);
            prev.setEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        requireContext().unregisterReceiver(sessionsReceiver);
        super.onDestroy();
    }
}
