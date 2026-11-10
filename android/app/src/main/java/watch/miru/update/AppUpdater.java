package app.hayase;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppUpdater {

  private static final String TAG = "app.hayase";
  private static final String APK_FILE_NAME = "hayase-app-update.apk";

  private AppUpdater() {
    // Utility class
  }

  public static void downloadAndInstallApk(@NonNull Context context, @NonNull String apiurl) {
    Context appContext = context.getApplicationContext();

    // Run download in background thread
    new Thread(() -> {
      try {
        if (TextUtils.isEmpty(apiurl)) {
          Log.w(TAG, "downloadAndInstallApk: URL is empty");
          return;
        }

        // Fetch latest release from GitHub API
        Log.i(TAG, "Fetching releases from: " + apiurl);
        ReleaseInfo releaseInfo = fetchLatestRelease(apiurl);
        if (releaseInfo == null) {
          Log.w(TAG, "Could not fetch release information from GitHub API");
          return;
        }

        Log.i(TAG, "Found APK: " + releaseInfo.apkUrl + " (version: " + releaseInfo.version + ")");

        // Get current app version
        String currentVersion = getCurrentAppVersion(appContext);
        if (currentVersion == null) {
          Log.w(TAG, "Could not get current app version");
          return;
        }

        // Compare versions
        if (!isNewerVersion(currentVersion, releaseInfo.version)) {
          Log.i(TAG, "Update version " + releaseInfo.version + " is not newer than current version " + currentVersion);
          return;
        }

        Log.i(TAG, "Newer version found: " + releaseInfo.version + " (current: " + currentVersion + ")");

        File destinationDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (destinationDir == null) {
          Log.e(TAG, "Unable to access external files directory for downloads");
          return;
        }

        File apkFile = new File(destinationDir, APK_FILE_NAME);

        // Delete existing file
        if (apkFile.exists() && !apkFile.delete()) {
          Log.w(TAG, "Existing update file could not be deleted");
        }

        // Download the APK
        Log.i(TAG, "Starting download from: " + releaseInfo.apkUrl);
        URL url = new URL(releaseInfo.apkUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
          Log.e(TAG, "Download failed with response code: " + responseCode);
          showToast(appContext, "Update download failed");
          return;
        }

        // Download file
        try (InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(apkFile)) {

          byte[] buffer = new byte[8192];
          int bytesRead;
          long totalBytes = 0;

          while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
          }

          Log.i(TAG, "Download completed. Total bytes: " + totalBytes);
        }

        connection.disconnect();

        // Verify file exists and has content
        if (!apkFile.exists() || apkFile.length() == 0) {
          Log.e(TAG, "Downloaded APK file is missing or empty");
          showToast(appContext, "Update download failed");
          return;
        }

        Log.i(TAG, "APK file ready: " + apkFile.getAbsolutePath() + " (" + apkFile.length() + " bytes)");

        // // Check install permission on Android O+
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // if (!appContext.getPackageManager().canRequestPackageInstalls()) {
        // showToast(appContext, "Please enable unknown sources");
        // Intent settingsIntent = new
        // Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        // .setData(Uri.parse("package:" + appContext.getPackageName()))
        // .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // appContext.startActivity(settingsIntent);
        // return;
        // }
        // }

        // Install the APK
        installApk(appContext, apkFile);

      } catch (Exception e) {
        Log.e(TAG, "Failed to download and install update", e);
        showToast(appContext, "Update failed");
      }
    }).start();
  }

  private static void installApk(@NonNull Context context, @NonNull File apkFile) {
    try {
      Uri apkUri = FileProvider.getUriForFile(
          context,
          context.getPackageName() + ".fileprovider",
          apkFile);

      Log.i(TAG, "Installing APK from URI: " + apkUri);

      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

      context.startActivity(intent);
      Log.i(TAG, "Installer launched successfully");

    } catch (Exception e) {
      Log.e(TAG, "Failed to launch installer", e);
      showToast(context, "Unable to install update");
    }
  }

  private static void showToast(@NonNull Context context, @NonNull String message) {
    // Post to main thread for Toast
    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
    mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
  }

  /**
   * Fetches the latest release info and extracts APK download URL + version.
   * 
   *    {"android-6.4.16.apk":"https://.../android-6.4.16.apk", ...}
   *
   * @param apiUrl API endpoint returning release metadata
   * @return ReleaseInfo containing APK URL and version, or null if not found
   */
  @Nullable
  private static ReleaseInfo fetchLatestRelease(@NonNull String apiUrl) {
    try {
      URL url = new URL(apiUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setDoInput(true);
      connection.connect();

      int responseCode = connection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        Log.e(TAG, "GitHub API request failed with response code: " + responseCode);
        return null;
      }

      // Read JSON response
      StringBuilder jsonBuilder = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          jsonBuilder.append(line);
        }
      }

      connection.disconnect();

      JSONObject files = new JSONObject(jsonBuilder.toString().trim());

      // Prefer the APK for the ABI of the device. Use the universal APK if there is none.
      ReleaseInfo universal = null;
      java.util.Map<String, ReleaseInfo> byAbi = new java.util.HashMap<>();
      for (java.util.Iterator<String> it = files.keys(); it.hasNext();) {
        String fileName = it.next();
        if (fileName == null || !fileName.endsWith(".apk")) {
          continue;
        }

        String downloadUrl = files.optString(fileName, null);
        if (TextUtils.isEmpty(downloadUrl)) {
          continue;
        }

        String version = extractVersionFromUrl(fileName);
        if (version == null) {
          continue;
        }

        String abi = abiFromFileName(fileName);
        if (abi != null) {
          byAbi.put(abi, new ReleaseInfo(downloadUrl, version));
        } else if (universal == null) {
          universal = new ReleaseInfo(downloadUrl, version);
        }
      }

      for (String abi : Build.SUPPORTED_ABIS) {
        ReleaseInfo info = byAbi.get(abi);
        if (info != null) {
          return info;
        }
      }
      if (universal != null) {
        return universal;
      }

      Log.w(TAG, "No .apk file for this device found in API response");
      return null;

    } catch (Exception e) {
      Log.e(TAG, "Failed to fetch release info", e);
      return null;
    }
  }

  private static final String[] APK_ABIS = { "armeabi-v7a", "arm64-v8a", "x86", "x86_64" };

  /**
   * Returns the ABI of a split APK name such as android-6.5.6-arm64-v8a.apk,
   * or null for the universal APK.
   */
  @Nullable
  private static String abiFromFileName(@NonNull String fileName) {
    for (String abi : APK_ABIS) {
      if (fileName.endsWith("-" + abi + ".apk")) {
        return abi;
      }
    }
    return null;
  }

  /**
   * Extracts version string from APK URL using the LAST occurrence.
   * Handles formats like:
   * -
   * https://github.com/owner/repo/releases/download/v6.4.32/android-6.4.10.apk
   * - Returns "6.4.10" (from the filename, not the tag)
   */
  @Nullable
  private static String extractVersionFromUrl(@NonNull String url) {
    // Pattern to match version numbers in various formats
    // Matches v6.4.32 or android-6.4.32 in the URL
    Pattern pattern = Pattern.compile("(?:v|android-)?(\\d+\\.\\d+\\.\\d+)");
    Matcher matcher = pattern.matcher(url);

    String lastMatch = null;
    while (matcher.find()) {
      lastMatch = matcher.group(1);
    }

    return lastMatch;
  }

  /**
   * Gets the current app version from PackageManager.
   */
  @Nullable
  private static String getCurrentAppVersion(@NonNull Context context) {
    try {
      PackageInfo packageInfo = context.getPackageManager()
          .getPackageInfo(context.getPackageName(), 0);
      return packageInfo.versionName;
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Failed to get package info", e);
      return null;
    }
  }

  /**
   * Compares two version strings to determine if the new version is newer.
   * Assumes semantic versioning (major.minor.patch).
   * 
   * @param currentVersion The current app version (e.g., "6.4.30")
   * @param newVersion     The version from the URL (e.g., "6.4.32")
   * @return true if newVersion is newer than currentVersion
   */
  private static boolean isNewerVersion(@NonNull String currentVersion, @NonNull String newVersion) {
    String[] currentParts = currentVersion.split("\\.");
    String[] newParts = newVersion.split("\\.");

    int length = Math.max(currentParts.length, newParts.length);
    for (int i = 0; i < length; i++) {
      int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
      int newPart = i < newParts.length ? parseVersionPart(newParts[i]) : 0;

      if (newPart > currentPart) {
        return true;
      } else if (newPart < currentPart) {
        return false;
      }
    }
    return false; // Versions are equal
  }

  /**
   * Parses a version part string to an integer, ignoring any non-numeric suffix.
   * E.g., "32-beta" becomes 32.
   */
  private static int parseVersionPart(@NonNull String part) {
    try {
      // Extract only the numeric prefix
      Matcher matcher = Pattern.compile("^(\\d+)").matcher(part);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
      return 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Container for release information from GitHub API.
   */
  private static class ReleaseInfo {
    final String apkUrl;
    final String version;

    ReleaseInfo(String apkUrl, String version) {
      this.apkUrl = apkUrl;
      this.version = version;
    }
  }
}
