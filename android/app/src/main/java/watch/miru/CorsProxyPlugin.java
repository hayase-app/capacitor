package app.hayase;

import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@CapacitorPlugin(name = "CorsProxy")
public class CorsProxyPlugin extends Plugin {
  private static final Set<String> corsEnabledUrls = Collections.synchronizedSet(new HashSet<>());

  @PluginMethod
  public void enableCORS(PluginCall call) {
    JSArray urls = call.getArray("urls");

    corsEnabledUrls.clear();

    if (urls != null) {
      for (int i = 0; i < urls.length(); i++) {
        String url = urls.optString(i, null);
        if (url != null && !url.isEmpty()) {
          corsEnabledUrls.add(url);
        }
      }
    }

    call.resolve();
  }

  public static boolean isCorsEnabled(String url) {
    if (url == null) {
      return false;
    }

    synchronized (corsEnabledUrls) {
      for (String prefix : corsEnabledUrls) {
        if (url.startsWith(prefix)) {
          return true;
        }
      }
    }

    return false;
  }
}
