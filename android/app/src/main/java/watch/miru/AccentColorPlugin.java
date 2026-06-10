package app.hayase;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;

import androidx.core.graphics.ColorUtils;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AccentColor")
public class AccentColorPlugin extends Plugin {
  @PluginMethod
  public void getAccentColor(PluginCall call) {
    Integer accentColor = getSystemAccentColor();
    if (accentColor != null) {
      String hexColor = String.format("#%06X", 0xFFFFFF & accentColor);
      JSObject ret = new JSObject();
      ret.put("color", hexColor);
      call.resolve(ret);
      return;
    }

    String fallback = call.getString("fallback");
    if (fallback != null) {
      JSObject ret = new JSObject();
      ret.put("color", fallback);
      call.resolve(ret);
      return;
    }

    call.reject("No accent color available");
  }

  private Integer getSystemAccentColor() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      try {
        int colorId = Resources.getSystem().getIdentifier("system_accent1_500", "color", "android");
        if (colorId != 0) {
          return getContext().getResources().getColor(colorId, getContext().getTheme());
        }
      } catch (Exception ignored) {}

      try {
        WallpaperManager wm = getContext().getSystemService(WallpaperManager.class);
        WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
        if (wc != null) {
          Color best = pickBestAccent(wc);
          if (best != null) {
            float[] hsl = new float[3];
            ColorUtils.colorToHSL(best.toArgb(), hsl);
            hsl[2] = 0.50f;
            return ColorUtils.HSLToColor(hsl);
          }
        }
      } catch (Exception ignored) {}
    }

    try {
      TypedValue typedValue = new TypedValue();
      if (getContext().getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
        int type = typedValue.type;
        if (type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT) {
          return typedValue.data;
        }
      }
    } catch (Exception ignored) {}

    return null;
  }

  private Color pickBestAccent(WallpaperColors wc) {
    Color[] candidates = { wc.getPrimaryColor(), wc.getSecondaryColor(), wc.getTertiaryColor() };
    Color best = null;
    float bestScore = -1;

    for (Color c : candidates) {
      if (c == null) continue;
      float[] hsl = new float[3];
      ColorUtils.colorToHSL(c.toArgb(), hsl);
      float sat = hsl[1];
      float lightScore = 1.0f - Math.abs(hsl[2] - 0.5f) * 2.0f;
      float score = sat * 0.7f + lightScore * 0.3f;
      if (score > bestScore) {
        bestScore = score;
        best = c;
      }
    }

    return best;
  }
}
