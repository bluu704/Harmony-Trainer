package com.harmony.trainer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {

    private WebView web;
    private PermissionRequest pendingMic;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        getWindow().setStatusBarColor(Color.parseColor("#06070B"));
        getWindow().setNavigationBarColor(Color.parseColor("#06070B"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#06070B"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(WebSettings.FORCE_DARK_OFF);
        }

        // file:// 은 보안 컨텍스트가 아니라 마이크를 쓸 수 없다.
        // 앱 안의 파일을 https://appassets.androidplatform.net 주소로 넘겨준다.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
        });

        // 튜너가 마이크를 요청하면 WebView에 권한을 넘겨준다
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                    == PackageManager.PERMISSION_GRANTED) {
                                // 요청된 리소스를 그대로 돌려준다
                                request.grant(request.getResources());
                            } else {
                                // 안드로이드 권한부터 받고, 허용되면 튜너를 다시 켠다
                                pendingMic = request;
                                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                            }
                            return;
                        }
                    }
                    request.deny();
                });
            }
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        setContentView(web);

        // 튜너를 바로 쓸 수 있도록 마이크 권한을 미리 물어본다
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (pendingMic != null) {
            if (granted) {
                pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingMic.deny();
            }
            pendingMic = null;
        }
        if (granted && web != null) {
            // 권한을 받은 뒤 튜너를 자동으로 다시 시작
            web.evaluateJavascript("if(typeof toggleTuner==='function')toggleTuner();", null);
        }
    }

    @Override
    public void onBackPressed() {
        // 앱 안에서 시트나 화면이 열려 있으면 그것부터 닫는다
        web.evaluateJavascript(
            "(function(){try{return (typeof goBackApp==='function' && goBackApp()) ? '1' : '0';}catch(e){return '0';}})()",
            value -> {
                if (value == null || !value.contains("1")) {
                    finish();
                }
            });
    }
}
