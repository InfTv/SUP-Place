package com.supplace.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.Manifest;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SupPlaceActivity extends Activity {
    private static final int REQUEST_IMPORT_REPORT = 1001;
    private static final int REQUEST_EXPORT_REPORT = 1002;
    private static final int REQUEST_NOTIFICATIONS = 1003;
    private static final String NOTIFICATION_PREFS = "supplace_update_notifications";
    private static final int MAX_REPORT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_VERSION_BYTES = 256 * 1024;
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/InfTv/SUP-Place/main/version.json";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final int MAX_CLOUD_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CLOUD_ERROR_BYTES = 256 * 1024;
    private static final String SUPABASE_URL = "https://tqhesbyebqbqzepexgyo.supabase.co";
    private static final String SUPABASE_PUBLISHABLE_KEY = "sb_publishable_jHiQlleA5yZS23F_CXBZnA_Waf8LDOs";

    private WebView webView;
    private String pendingImportedReport;
    private String pendingExportReport;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private long updateDownloadId = -1L;
    private String expectedUpdateSha256 = "";

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (completedId == updateDownloadId) {
                handleCompletedUpdate(completedId);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWebView();
        scheduleDailyUpdateCheck();
        requestNotificationPermissionIfNeeded();
        registerDownloadReceiver();
        captureReportFromIntent(getIntent());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(NOTIFICATION_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean("permission_asked", false)) {
            return;
        }
        prefs.edit().putBoolean("permission_asked", true).apply();
        requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS
        );
    }

    private void scheduleDailyUpdateCheck() {
        try {
            JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (scheduler.getPendingJob(UpdateCheckJobService.JOB_ID) != null) {
                    return;
                }
            } else {
                for (JobInfo job : scheduler.getAllPendingJobs()) {
                    if (job.getId() == UpdateCheckJobService.JOB_ID) {
                        return;
                    }
                }
            }
            JobInfo job = new JobInfo.Builder(
                    UpdateCheckJobService.JOB_ID,
                    new ComponentName(this, UpdateCheckJobService.class)
            )
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setPeriodic(24L * 60L * 60L * 1000L)
                    .build();
            scheduler.schedule(job);
        } catch (Exception ignored) {
            // Background update checks must never affect normal app startup.
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        webView.addJavascriptInterface(this, "Android");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @JavascriptInterface
    public void exportReport(String json, String fileName) {
        runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Uri uri = createReportInDownloads(json, safeReportName(fileName));
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Отправить отчёт SUP Place"));
                    callJavaScript("reportExported()");
                } else {
                    pendingExportReport = json;
                    Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, safeReportName(fileName));
                    startActivityForResult(create, REQUEST_EXPORT_REPORT);
                }
            } catch (Exception error) {
                callJavaScript("reportExportFailed()");
            }
        });
    }

    @JavascriptInterface
    public void importReport() {
        runOnUiThread(() -> {
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/json", "application/x-supplace", "text/plain"
                    });
            startActivityForResult(open, REQUEST_IMPORT_REPORT);
        });
    }

    @JavascriptInterface
    public synchronized String takeImportedReport() {
        String report = pendingImportedReport == null ? "" : pendingImportedReport;
        pendingImportedReport = null;
        return report;
    }

    @JavascriptInterface
    public void consumeLaunchReport() {
        // Legacy JS bridge name retained for compatibility. It now polls the
        // current DownloadManager job instead of relying on a runtime receiver.
        runOnUiThread(() -> handleCompletedUpdate(updateDownloadId));
    }

    @JavascriptInterface
    public void checkForUpdate() {
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty(
                        "User-Agent", "SUP-Place/" + BuildConfig.VERSION_NAME
                );
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("HTTP " + responseCode);
                }
                String body;
                try (InputStream input = connection.getInputStream()) {
                    body = readUtf8(input, MAX_VERSION_BYTES);
                }
                JSONObject remote = new JSONObject(body);
                int remoteCode = remote.getInt("versionCode");
                String remoteName = remote.optString(
                        "versionName",
                        remote.optString("version", "")
                ).trim();
                if (remoteName.isEmpty()) {
                    throw new IOException("Missing version name");
                }
                String apkUrl = remote.getString("apkUrl");
                if (!isTrustedApkUrl(apkUrl)) {
                    throw new IOException("Untrusted APK URL");
                }
                JSONObject result = new JSONObject();
                result.put("ok", true);
                result.put("available", remoteCode > BuildConfig.VERSION_CODE);
                result.put("currentVersionCode", BuildConfig.VERSION_CODE);
                result.put("currentVersionName", BuildConfig.VERSION_NAME);
                result.put("versionCode", remoteCode);
                result.put("versionName", remoteName);
                result.put("apkUrl", apkUrl);
                result.put("changelog", remote.optString("changelog", ""));
                String sha256 = remote.getString("sha256").trim();
                if (!sha256.matches("(?i)[0-9a-f]{64}")) {
                    throw new IOException("Invalid APK hash");
                }
                result.put("sha256", sha256.toUpperCase(Locale.ROOT));
                deliverUpdateResult(result);
            } catch (Exception error) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("ok", false);
                    result.put("message", "Не удалось проверить обновления");
                    deliverUpdateResult(result);
                } catch (Exception ignored) {
                    // JSONObject construction with fixed strings cannot fail in practice.
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @JavascriptInterface
    public void downloadAndInstallUpdate(
            String apkUrl,
            String versionName,
            String expectedSha256
    ) {
        runOnUiThread(() -> {
            try {
                if (!isTrustedApkUrl(apkUrl)) {
                    throw new IOException("Untrusted APK URL");
                }
                if (expectedSha256 == null
                        || !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
                    throw new IOException("Invalid APK hash");
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && !getPackageManager().canRequestPackageInstalls()) {
                    Intent permission = new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())
                    );
                    startActivity(permission);
                    deliverDownloadState(
                            "permission",
                            "Разреши установку обновлений и нажми «Скачать» ещё раз"
                    );
                    return;
                }

                DownloadManager manager =
                        (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                String safeVersion = versionName == null
                        ? "update"
                        : versionName.replaceAll("[^0-9A-Za-z._-]", "_");
                String fileName = "SUP_Place_" + safeVersion + ".apk";
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl))
                        .setTitle("SUP Place " + safeVersion)
                        .setDescription("Загрузка обновления")
                        .setMimeType(APK_MIME)
                        .setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        .setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                fileName
                        );
                updateDownloadId = manager.enqueue(request);
                this.expectedUpdateSha256 = expectedSha256.toUpperCase(Locale.ROOT);
                deliverDownloadState("downloading", "Обновление скачивается");
            } catch (Exception error) {
                deliverDownloadState("error", "Не удалось начать загрузку обновления");
            }
        });
    }


    @JavascriptInterface
    public void cloudRpc(String requestId, String functionName, String paramsJson) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,96}")
                || !isAllowedCloudFunction(functionName)) {
            deliverCloudResult(requestId == null ? "" : requestId, false,
                    "{\"message\":\"INVALID_REQUEST\"}", 400);
            return;
        }
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                byte[] body = (paramsJson == null || paramsJson.trim().isEmpty() ? "{}" : paramsJson)
                        .getBytes(StandardCharsets.UTF_8);
                if (body.length > MAX_CLOUD_BYTES) {
                    throw new IOException("Request is too large");
                }
                connection = (HttpURLConnection) new URL(
                        SUPABASE_URL + "/rest/v1/rpc/" + functionName
                ).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(20_000);
                connection.setDoOutput(true);
                connection.setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("User-Agent", "SUP-Place/" + BuildConfig.VERSION_NAME);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
                int status = connection.getResponseCode();
                boolean ok = status >= 200 && status < 300;
                InputStream stream = ok ? connection.getInputStream() : connection.getErrorStream();
                String response = stream == null ? "" : readUtf8(
                        stream,
                        ok ? MAX_CLOUD_BYTES : MAX_CLOUD_ERROR_BYTES
                );
                deliverCloudResult(requestId, ok, response, status);
            } catch (Exception error) {
                deliverCloudResult(
                        requestId,
                        false,
                        "{\"message\":\"NETWORK_ERROR\"}",
                        0
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static boolean isAllowedCloudFunction(String name) {
        if (name == null) {
            return false;
        }
        switch (name) {
            case "supplace_pair_device":
            case "supplace_device_status":
            case "supplace_submit_daily_report":
            case "supplace_owner_login":
            case "supplace_owner_session":
            case "supplace_owner_logout":
            case "supplace_owner_locations":
            case "supplace_owner_add_location":
            case "supplace_owner_create_pair_code":
            case "supplace_owner_devices":
            case "supplace_owner_set_device_active":
            case "supplace_owner_reports":
            case "supplace_owner_report":
                return true;
            default:
                return false;
        }
    }

    private void deliverCloudResult(String requestId, boolean ok, String body, int status) {
        callJavaScript(
                "onCloudRpcResult(" + JSONObject.quote(requestId) + ","
                        + (ok ? "true" : "false") + ","
                        + JSONObject.quote(body == null ? "" : body) + ","
                        + status + ")"
        );
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureReportFromIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQUEST_EXPORT_REPORT) {
                pendingExportReport = null;
            }
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT_REPORT) {
            readImportedReport(uri);
        } else if (requestCode == REQUEST_EXPORT_REPORT) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null || pendingExportReport == null) {
                    throw new IOException("No export destination");
                }
                output.write(pendingExportReport.getBytes(StandardCharsets.UTF_8));
                pendingExportReport = null;
                callJavaScript("reportExported()");
            } catch (Exception error) {
                pendingExportReport = null;
                callJavaScript("reportExportFailed()");
            }
        }
    }

    @Override
    public void onBackPressed() {
        callJavaScript("androidBack()");
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(downloadReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was not registered.
        }
        networkExecutor.shutdownNow();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private Uri createReportInDownloads(String json, String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SUP Place");
        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Unable to create report");
        }
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Unable to open report");
            }
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return uri;
    }

    private void captureReportFromIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri uri = intent.getData();
        if (uri == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                //noinspection deprecation
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            ClipData clip = intent.getClipData();
            if (uri == null && clip != null && clip.getItemCount() > 0) {
                uri = clip.getItemAt(0).getUri();
            }
        }
        if (uri != null) {
            readImportedReport(uri);
        }
    }

    private void readImportedReport(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open report");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_REPORT_BYTES) {
                    throw new IOException("Report is too large");
                }
                output.write(buffer, 0, read);
            }
            synchronized (this) {
                pendingImportedReport = output.toString(StandardCharsets.UTF_8.name());
            }
            callJavaScript("consumeNativeImport()");
        } catch (Exception error) {
            callJavaScript("reportImportFailed()");
        }
    }

    private void registerDownloadReceiver() {
        // Intentionally empty. Dynamic ACTION_DOWNLOAD_COMPLETE receiver flags
        // caused either missed events or launch crashes on newer Android.
        // Completion is polled through DownloadManager from the WebView bridge.
    }

    private void handleCompletedUpdate(long downloadId) {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IOException("Missing download");
            }
            int statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
            if (cursor.getInt(statusColumn) != DownloadManager.STATUS_SUCCESSFUL) {
                throw new IOException("Download not complete");
            }
        } catch (Exception error) {
            deliverDownloadState("error", "Не удалось скачать обновление");
            return;
        }

        Uri apk = manager.getUriForDownloadedFile(downloadId);
        if (apk == null) {
            deliverDownloadState("error", "Скачанный APK не найден");
            return;
        }
        try {
            String actualSha256 = sha256(apk);
            if (!actualSha256.equals(expectedUpdateSha256)) {
                manager.remove(downloadId);
                deliverDownloadState("error", "Проверка APK не пройдена — файл удалён");
                return;
            }
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apk, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
            deliverDownloadState("installing", "Открыт установщик Android");
        } catch (Exception error) {
            deliverDownloadState("error", "Не удалось открыть установщик Android");
        }
    }

    private void deliverUpdateResult(JSONObject result) {
        callJavaScript("onUpdateCheckResult(" + JSONObject.quote(result.toString()) + ")");
    }

    private void deliverDownloadState(String state, String message) {
        callJavaScript(
                "onUpdateDownloadState(" + JSONObject.quote(state) + ","
                        + JSONObject.quote(message) + ")"
        );
    }

    private static boolean isTrustedApkUrl(String value) {
        if (value == null) {
            return false;
        }
        Uri uri = Uri.parse(value);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("github.com")
                || host.endsWith(".github.com")
                || host.equals("githubusercontent.com")
                || host.endsWith(".githubusercontent.com");
    }

    private static String readUtf8(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Response is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private String sha256(Uri uri) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to read downloaded APK");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02X", value & 0xFF));
        }
        return hex.toString();
    }

    private void callJavaScript(String expression) {
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript("javascript:" + expression, null));
        }
    }

    private static String safeReportName(String requested) {
        String name = requested == null ? "SUP_Place_report.supplace.json" : requested;
        name = name.replaceAll("[^A-Za-z0-9А-Яа-я._-]", "_");
        return name.endsWith(".json") ? name : name + ".supplace.json";
    }
}
