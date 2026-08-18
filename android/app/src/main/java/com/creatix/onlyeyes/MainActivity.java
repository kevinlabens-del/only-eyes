package com.creatix.onlyeyes;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://kevinlabens-del.github.io/only-eyes/";
    private static final int REQ_PERMISSIONS = 1001;
    private WebView webView;
    private final Map<String, DownloadSession> downloadSessions = new ConcurrentHashMap<>();

    private static final class DownloadSession {
        OutputStream stream;
        Uri uri;
        File file;
        String name;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        requestRuntimePermissions();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " OnlyEyesAndroid/1.1");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidDownloads(), "AndroidDownloads");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                        "(()=>{const b=document.getElementById('installBtn');if(b)b.style.setProperty('display','none','important');})();",
                        null
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    List<String> granted = new ArrayList<>();
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) &&
                                checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            granted.add(r);
                        }
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) &&
                                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            granted.add(r);
                        }
                    }
                    if (granted.isEmpty()) request.deny();
                    else request.grant(granted.toArray(new String[0]));
                });
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url == null) return;
            if (url.startsWith("blob:")) {
                downloadBlob(url);
                return;
            }
            if (!url.startsWith("http")) {
                Toast.makeText(this, "Format de téléchargement non pris en charge.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
            } catch (Exception e) {
                Toast.makeText(this, "Téléchargement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        if (savedInstanceState == null) webView.loadUrl(APP_URL);
        else webView.restoreState(savedInstanceState);
    }

    private void downloadBlob(String blobUrl) {
        String quotedUrl = JSONObject.quote(blobUrl);
        String js = "(async()=>{try{" +
                "const u=" + quotedUrl + ";" +
                "const r=await fetch(u);" +
                "const b=await r.blob();" +
                "const ext=(b.type&&b.type.includes('mp4'))?'mp4':'webm';" +
                "const name='OnlyEyes-'+new Date().toISOString().replace(/[:.]/g,'-')+'.'+ext;" +
                "const token=AndroidDownloads.beginDownload(name,b.type||'video/webm');" +
                "if(!token)throw new Error('Impossible de créer le fichier Android');" +
                "const size=262144;" +
                "for(let o=0;o<b.size;o+=size){" +
                " const a=new Uint8Array(await b.slice(o,Math.min(o+size,b.size)).arrayBuffer());" +
                " let bin='';" +
                " for(let i=0;i<a.length;i+=32768)bin+=String.fromCharCode.apply(null,a.subarray(i,Math.min(i+32768,a.length)));" +
                " AndroidDownloads.appendChunk(token,btoa(bin));" +
                "}" +
                "AndroidDownloads.finishDownload(token);" +
                "}catch(e){AndroidDownloads.failDownload(String(e));}})();";
        webView.evaluateJavascript(js, null);
        Toast.makeText(this, "Enregistrement de la vidéo…", Toast.LENGTH_SHORT).show();
    }

    private final class AndroidDownloads {
        @JavascriptInterface
        public String beginDownload(String requestedName, String mimeType) {
            try {
                String token = UUID.randomUUID().toString();
                String name = sanitizeFileName(requestedName);
                if (name.isEmpty()) {
                    name = "OnlyEyes-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".webm";
                }

                DownloadSession session = new DownloadSession();
                session.name = name;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType == null || mimeType.isEmpty() ? "video/webm" : mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    session.uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (session.uri == null) throw new IllegalStateException("MediaStore indisponible");
                    session.stream = resolver.openOutputStream(session.uri, "w");
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Dossier Téléchargements inaccessible");
                    session.file = uniqueFile(dir, name);
                    session.name = session.file.getName();
                    session.stream = new FileOutputStream(session.file);
                }

                if (session.stream == null) throw new IllegalStateException("Flux de fichier indisponible");
                downloadSessions.put(token, session);
                return token;
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Impossible de préparer la vidéo : " + e.getMessage(), Toast.LENGTH_LONG).show());
                return "";
            }
        }

        @JavascriptInterface
        public void appendChunk(String token, String base64Chunk) {
            DownloadSession session = downloadSessions.get(token);
            if (session == null || session.stream == null) return;
            try {
                byte[] data = Base64.decode(base64Chunk, Base64.DEFAULT);
                synchronized (session) {
                    session.stream.write(data);
                }
            } catch (Exception e) {
                abortSession(token);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Erreur pendant l’enregistrement : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void finishDownload(String token) {
            DownloadSession session = downloadSessions.remove(token);
            if (session == null) return;
            try {
                synchronized (session) {
                    if (session.stream != null) {
                        session.stream.flush();
                        session.stream.close();
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && session.uri != null) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(session.uri, done, null, null);
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Vidéo enregistrée dans Téléchargements : " + session.name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Finalisation impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void failDownload(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Téléchargement vidéo impossible : " + message, Toast.LENGTH_LONG).show());
        }
    }

    private void abortSession(String token) {
        DownloadSession session = downloadSessions.remove(token);
        if (session == null) return;
        try { if (session.stream != null) session.stream.close(); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && session.uri != null) {
            try { getContentResolver().delete(session.uri, null, null); } catch (Exception ignored) {}
        } else if (session.file != null) {
            try { session.file.delete(); } catch (Exception ignored) {}
        }
    }

    private static String sanitizeFileName(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
    }

    private static File uniqueFile(File dir, String requestedName) {
        File candidate = new File(dir, requestedName);
        if (!candidate.exists()) return candidate;
        int dot = requestedName.lastIndexOf('.');
        String base = dot > 0 ? requestedName.substring(0, dot) : requestedName;
        String ext = dot > 0 ? requestedName.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            candidate = new File(dir, base + "-" + i + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        for (String token : new ArrayList<>(downloadSessions.keySet())) abortSession(token);
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.removeJavascriptInterface("AndroidDownloads");
            webView.destroy();
        }
        super.onDestroy();
    }
}
