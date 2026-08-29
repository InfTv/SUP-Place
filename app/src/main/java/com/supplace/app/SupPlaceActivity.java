package com.supplace.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class SupPlaceActivity extends Activity {
    private static final int REQUEST_IMPORT_REPORT = 1001;
    private static final int REQUEST_EXPORT_REPORT = 1002;
    private static final int MAX_REPORT_BYTES = 8 * 1024 * 1024;

    private WebView webView;
    private String pendingImportedReport;
    private String pendingExportReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWebView();
        captureReportFromIntent(getIntent());
    }

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
        runOnUiThread(() -> captureReportFromIntent(getIntent()));
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

