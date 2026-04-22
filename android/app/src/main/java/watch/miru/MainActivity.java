package app.hayase;

import android.app.Dialog;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSArray;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import net.hampoelz.capacitor.nodejs.CapacitorNodeJSPlugin;
import net.hampoelz.capacitor.nodejs.CapacitorNodeJS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends BridgeActivity {
  private final Map<WebView, Dialog> popupDialogs = new HashMap<>();
  protected RequestQueue queue = null;

  private static final int FILE_CHOOSER_REQUEST_CODE = 61453;
  private ValueCallback<Uri[]> filePathCallback;

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
      return;
    }

    if (filePathCallback == null) {
      return;
    }

    Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
    filePathCallback.onReceiveValue(results);
    filePathCallback = null;
  }

  private void startNodeEngineWithCustomArgs() {
    try {
      CapacitorNodeJSPlugin capacitorNodeJS = (CapacitorNodeJSPlugin) getBridge().getPlugin("CapacitorNodeJS")
          .getInstance();

      Field f = CapacitorNodeJSPlugin.class.getDeclaredField("implementation");
      f.setAccessible(true);
      CapacitorNodeJS implementation = (CapacitorNodeJS) f.get(capacitorNodeJS);

      String[] nodeArgs = new String[] {
        "--disallow-code-generation-from-strings",
        "--disable-proto=throw",
        "--frozen-intrinsics"
      };

      Method m = CapacitorNodeJS.class.getDeclaredMethod(
          "startEngine",
          com.getcapacitor.PluginCall.class,
          String.class,
          String.class,
          String[].class,
          Map.class);
      m.setAccessible(true);
      m.invoke(implementation, null, "nodejs", null, nodeArgs, new HashMap<>());
      Log.i("NodeJS", "Started NodeJS engine with custom args");
    } catch (Exception e) {
      Log.e("NodeJS", "Failed to start NodeJS engine with custom args", e);
    }
  }

  @Override
  public void onDestroy() {
    try {
      CapacitorNodeJSPlugin capacitorNodeJS = (CapacitorNodeJSPlugin) getBridge().getPlugin("CapacitorNodeJS")
          .getInstance();
      Field f = CapacitorNodeJSPlugin.class.getDeclaredField("implementation");
      f.setAccessible(true);
      CapacitorNodeJS implementation = (CapacitorNodeJS) f.get(capacitorNodeJS);
      Method m = CapacitorNodeJS.class.getDeclaredMethod("sendMessage", String.class, String.class, JSArray.class);
      m.setAccessible(true);
      m.invoke(implementation, "EVENT_CHANNEL", "destroy", new JSArray());
      Log.i("Destroy", "Sent destroy message to NodeJS");
    } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      Log.e("Destroy", "Failed to send destroy message to NodeJS", e);
    }

    super.onDestroy();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    this.queue = Volley.newRequestQueue(this);
    registerPlugin(MediaNotificationPlugin.class);
    // registerPlugin(EngagePlugin.class);
    registerPlugin(FilesystemPlugin.class);
    registerPlugin(CorsProxyPlugin.class);

    super.onCreate(savedInstanceState);

    startNodeEngineWithCustomArgs();

    try {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
        WebView.setWebContentsDebuggingEnabled(true);
      }
      // Attempt to set command line flags (may not work on all devices/versions, if
      // at all)
      String commandLine = "--enable-blink-features=AudioVideoTracks --enable-experimental-web-platform-features";
      System.setProperty("chromium.command_line", commandLine);
    } catch (Exception e) {
      e.printStackTrace();
    }

    final WebView webView = getBridge().getWebView();
    webView.setBackgroundColor(Color.TRANSPARENT);
    View webViewParent = (View) webView.getParent();
    if (webViewParent != null) {
      webViewParent.setBackgroundColor(Color.TRANSPARENT);
      // Capacitor 8 SystemBars may apply top/bottom padding to this parent view,
      // which shows up as opaque bars. Keep padding zero so content can extend
      // behind transparent system bars.
      ViewCompat.setOnApplyWindowInsetsListener(webViewParent, (v, insets) -> {
        v.setPadding(0, 0, 0, 0);
        return insets;
      });
      ViewCompat.requestApplyInsets(webViewParent);
    }

    AppUpdater.downloadAndInstallApk(this, "https://api.hayase.watch/latest");

    WebSettings settings = webView.getSettings();
    settings.setSupportMultipleWindows(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(true);
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(true);

    configureSystemBars();

    // Set user agent to include AndroidTV if device supports Leanback or was
    // launched with LEANBACK_LAUNCHER
    boolean isLeanback = getPackageManager().hasSystemFeature("android.software.leanback")
        || getPackageManager().hasSystemFeature("android.software.leanback_only");
    boolean isLeanbackIntent = false;
    if (getIntent() != null && getIntent().getCategories() != null) {
      isLeanbackIntent = getIntent().getCategories().contains("android.intent.category.LEANBACK_LAUNCHER");
    }
    if (isLeanback || isLeanbackIntent) {
      String ua = settings.getUserAgentString();
      if (!ua.contains("AndroidTV")) {
        settings.setUserAgentString(ua + " AndroidTV");
      }
    }

    webView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageFinished(WebView view, String url) {
        // Only inject JavaScript for allowed URLs.
        super.onPageFinished(view, url);
        if (url != null && (url.startsWith("https://hayase.app") || url.startsWith("http://localhost"))) {
          injectJavaScript(view);
          configureSystemBars();
        }
      }

      @Override
      public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) {
          return super.shouldInterceptRequest(view, request);
        }

        String urlString = request.getUrl().toString();

        boolean isMAL = urlString.startsWith("https://myanimelist.net/v1/oauth2")
            || urlString.startsWith("https://api.myanimelist.net/v2/");

        boolean isAL = urlString.startsWith("https://graphql.anilist.co");

        boolean isCorsProxy = CorsProxyPlugin.isCorsEnabled(urlString);

        if (!isMAL && !isAL && !isCorsProxy) {
          return super.shouldInterceptRequest(view, request);
        }

        boolean isOptions = "OPTIONS".equals(request.getMethod());

        if (isAL && !isOptions) {
          return super.shouldInterceptRequest(view, request);
        }

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Access-Control-Allow-Origin", "*");
        responseHeaders.put("Cache-Control", "public, max-age=86400");
        responseHeaders.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        responseHeaders.put("access-control-max-age", "86400");
        responseHeaders.put("Access-Control-Allow-Headers", "*");
        responseHeaders.put("Access-Control-Allow-Credentials", "true");

        // Override MAL and custom CORS preflight requests
        if (isOptions) {
          return new WebResourceResponse("application/json", "UTF-8", 200, "OK", responseHeaders, null);
        }

        // Rewrite MAL and custom CORS responses
        HttpURLConnection connection = null;
        try {
          String originalUrl = urlString;
          URL url;
          String requestBody = null;

          // Extract query parameters and convert them to request body for POST/PATCH
          // requests, because Android requests don't contain the body in the
          // request object... bruh
          if ("POST".equals(request.getMethod()) || "PATCH".equals(request.getMethod())) {
            java.net.URI uri = new java.net.URI(originalUrl);
            String query = uri.getQuery();

            if (query != null && !query.isEmpty()) {
              // Remove query parameters from URL for the actual request
              String baseUrl = originalUrl.substring(0, originalUrl.indexOf('?'));
              url = new URL(baseUrl);

              // Use the query string as the request body
              requestBody = query;
            } else {
              url = new URL(originalUrl);
            }
          } else {
            url = new URL(originalUrl);
          }

          connection = (HttpURLConnection) url.openConnection();

          Map<String, String> requestHeaders = request.getRequestHeaders();
          for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
          }

          // Set the request method
          connection.setRequestMethod(request.getMethod());

          // Send the request body for POST/PATCH requests
          if (requestBody != null && ("POST".equals(request.getMethod()) || "PATCH".equals(request.getMethod()))) {
            connection.setDoOutput(true);

            // Write the request body
            try (java.io.OutputStream os = connection.getOutputStream()) {
              byte[] input = requestBody.getBytes("UTF-8");
              os.write(input, 0, input.length);
            }
          }

          int statusCode = connection.getResponseCode();
          String reasonPhrase = connection.getResponseMessage();
          String mimeType = connection.getContentType();
          String encoding = connection.getContentEncoding();

          // Use getErrorStream() for errors, getInputStream() for success
          InputStream responseStream;
          if (statusCode >= 200 && statusCode < 300) {
            responseStream = connection.getInputStream();
          } else {
            responseStream = connection.getErrorStream();
            // If errorStream is also null, create an empty stream
            if (responseStream == null) {
              responseStream = new java.io.ByteArrayInputStream(new byte[0]);
            }
          }

          Map<String, List<String>> headerFields = connection.getHeaderFields();
          for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            if (entry.getKey() != null) {
              responseHeaders.put(entry.getKey(), String.join(", ", entry.getValue()));
            }
          }

          return new WebResourceResponse(
              mimeType != null ? mimeType : "application/json",
              encoding != null ? encoding : "UTF-8",
              statusCode,
              reasonPhrase,
              responseHeaders,
              responseStream);

        } catch (Exception e) {
          Log.e("WebViewClient", "Error occurred while intercepting request", e);
          if (connection != null) {
            connection.disconnect();
          }
          return super.shouldInterceptRequest(view, request);
        }
      }

      @Override
      public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
          Log.e("WebView", "Render process gone for " + view.getUrl() + ". Did it crash? " + detail.didCrash());
        }

        if (view != null) {
          view.reload();
        }

        return true; // Prevent the app from crashing
      }
    });

    // make js window.open() work just like in a browser
    webView.setWebChromeClient(new WebChromeClient() {
      // these 2 overrides are needed to re-implement fullscreen mode which breaks by
      // overriding chromeClient
      private View mCustomView;
      private WebChromeClient.CustomViewCallback mCustomViewCallback;
      private int mOriginalSystemUiVisibility;

      @Override
      public void onShowCustomView(View view, CustomViewCallback callback) {
        if (mCustomView != null) {
          callback.onCustomViewHidden();
          return;
        }
        mCustomView = view;
        mCustomViewCallback = callback;

        configureFullscreenBars(true);

        // Add the custom view to the main content view, not decor view, to preserve
        // insets
        ViewGroup contentView = findViewById(android.R.id.content);
        contentView.addView(mCustomView, new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT));
      }

      @Override
      public void onHideCustomView() {
        if (mCustomView == null) {
          return;
        }
        ((ViewGroup) mCustomView.getParent()).removeView(mCustomView);
        mCustomView = null;
        configureFullscreenBars(false);
        if (mCustomViewCallback != null) {
          mCustomViewCallback.onCustomViewHidden();
        }
      }

      @Override
      public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        WebView popupWebView = new WebView(MainActivity.this);
        WebSettings webSettings = popupWebView.getSettings();

        WebSettings mainSettings = view.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(mainSettings.getDomStorageEnabled()); // AL requires this LOL
        webSettings.setAllowContentAccess(mainSettings.getAllowContentAccess());

        // Set the same WebChromeClient as the main WebView so window.close() works,
        // IMPORTANT
        popupWebView.setWebChromeClient(this);
        popupWebView.setWebViewClient(new WebViewClient() {
          @Override
          public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
              Log.e("WebViewPopup", "Render process gone. Did it crash? " + detail.didCrash());
            }

            Dialog dialog = popupDialogs.get(view);
            if (dialog != null) {
              dialog.dismiss();
            }

            return true; // Prevent the app from crashing
          }
        });

        final Dialog dialog = new Dialog(MainActivity.this);
        dialog.setContentView(popupWebView);
        dialog.show();

        android.view.Window window = dialog.getWindow();
        if (window != null) {
          android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
          int width = (int) (metrics.widthPixels - 30);
          int height = (int) (metrics.heightPixels - 30);
          window.setLayout(width, height);
        }

        popupDialogs.put(popupWebView, dialog);

        dialog.setOnDismissListener(dialogInterface -> {
          popupWebView.destroy();
          popupDialogs.remove(popupWebView);
        });

        // The system needs a WebView to be returned for the window creation.
        // We pass our new popup WebView back via the result message.
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popupWebView);
        resultMsg.sendToTarget();
        // We return true to indicate that we have handled the new window creation.
        return true;
      }

      @Override
      public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        // Cancel any previous callback.
        if (MainActivity.this.filePathCallback != null) {
          MainActivity.this.filePathCallback.onReceiveValue(null);
        }
        MainActivity.this.filePathCallback = filePathCallback;

        Intent intent;
        try {
          intent = fileChooserParams.createIntent();
        } catch (Exception e) {
          MainActivity.this.filePathCallback = null;
          return false;
        }

        try {
          startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
          return true;
        } catch (ActivityNotFoundException e) {
          MainActivity.this.filePathCallback.onReceiveValue(null);
          MainActivity.this.filePathCallback = null;
          return false;
        }
      }

      // Also handle closing the popup window from JavaScript (e.g., window.close()),
      // might not be needed
      @Override
      public void onCloseWindow(WebView window) {
        super.onCloseWindow(window);
        Dialog dialog = popupDialogs.get(window);
        if (dialog != null && dialog.isShowing()) {
          dialog.dismiss();
        }
      }
    });
  }

  // Keep system bars visible and transparent.
  private void configureSystemBars() {
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      getWindow().setStatusBarContrastEnforced(false);
      getWindow().setNavigationBarContrastEnforced(false);
    }

    configureFullscreenBars(false);
  }

    private void configureFullscreenBars(boolean fullscreen) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowInsetsControllerCompat controller =
          WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
      if (controller != null) {
        controller.hide(WindowInsetsCompat.Type.statusBars());
        if (fullscreen) {
          controller.hide(WindowInsetsCompat.Type.navigationBars());
        } else {
          controller.show(WindowInsetsCompat.Type.navigationBars());
        }
      }
      return;
    }

    View decorView = getWindow().getDecorView();
    if (fullscreen) {
      decorView.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    } else {
      decorView.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
  }

  private void injectJavaScript(WebView webView) {
    try {
      InputStream inputStream = getAssets().open("public/preload.js");
      byte[] buffer = new byte[inputStream.available()];
      inputStream.read(buffer);
      inputStream.close();

      String jsCode = new String(buffer, StandardCharsets.UTF_8);

      // The 'null' second argument is a callback that we don't need here.
      webView.evaluateJavascript(jsCode, null);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}