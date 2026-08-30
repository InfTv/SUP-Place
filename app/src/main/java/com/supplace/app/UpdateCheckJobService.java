package com.supplace.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateCheckJobService extends JobService {
    public static final int JOB_ID = 42020;

    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/InfTv/SUP-Place/main/version.json";
    private static final int MAX_VERSION_BYTES = 256 * 1024;
    private static final String PREFS = "supplace_update_notifications";
    private static final String LAST_NOTIFIED_CODE = "last_notified_code";
    private static final String CHANNEL_ID = "supplace_updates";
    private static final int NOTIFICATION_ID_BASE = 42000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        executor.execute(() -> {
            try {
                checkAndNotify();
            } catch (Exception ignored) {
                // Silent background failure; next scheduled run will try again.
            } finally {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void checkAndNotify() throws Exception {
        JSONObject remote = fetchVersionManifest();
        int remoteCode = remote.getInt("versionCode");
        int currentCode = installedVersionCode();
        if (remoteCode <= currentCode) return;

        String remoteName = remote.optString(
                "versionName",
                remote.optString("version", "")
        ).trim();
        if (remoteName.isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (remoteCode <= prefs.getInt(LAST_NOTIFIED_CODE, 0)) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Обновления SUP Place",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Уведомления о новых версиях SUP Place");
            manager.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(this, SupPlaceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pending = PendingIntent.getActivity(
                this, 0, openApp, pendingFlags
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Доступна версия " + remoteName)
                .setContentText("Нажми, чтобы открыть SUP Place и обновиться")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);

        manager.notify(NOTIFICATION_ID_BASE + (remoteCode % 1000), builder.build());
        prefs.edit().putInt(LAST_NOTIFIED_CODE, remoteCode).apply();
    }

    private JSONObject fetchVersionManifest() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SUP-Place-background-update-check");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode);
            }

            try (InputStream input = connection.getInputStream()) {
                return new JSONObject(readUtf8(input, MAX_VERSION_BYTES));
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    private int installedVersionCode() throws PackageManager.NameNotFoundException {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            long code = info.getLongVersionCode();
            return code > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) code;
        }
        return info.versionCode;
    }

    private static String readUtf8(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;

        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("Response is too large");
            output.write(buffer, 0, read);
        }

        return output.toString(StandardCharsets.UTF_8.name());
    }
}
