package app.hayase;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_FAST_FORWARD;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_REWIND;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.android.volley.Request;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PluginResult;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Objects;

@CapacitorPlugin(name = "MediaNotification")
public class MediaNotificationPlugin extends Plugin {
  private MediaSessionCompat current = null;
  private String lastUrl = null;
  private Bitmap currentImage = null;

  private MediaSessionCompat getSession() {
    if (current == null) {
      Log.i("app.hayase", "MediaSession created");
      current = new MediaSessionCompat(this.getContext(), "hayase-player");
      current.setCallback(new MediaCallback());
      current.setActive(true);

      try {
        Intent service = new Intent(this.getContext(), MediaNotificationService.class);
        service.putExtra("token", current.getSessionToken());
        getContext().startService(service);
      } catch (Exception e) {
        Log.e("MediaNotification", "Failed to start notification service", e);
      }
    }

    return current;
  }

  @Override
  protected void handleOnDestroy() {
    if (current != null) {
      getContext().stopService(new Intent(this.getContext(), MediaNotificationService.class));
      current.release();
      current = null;
    }
  }

  @PluginMethod
  public void setMediaSession(PluginCall call) {
    MediaSessionCompat session = getSession();
    Log.i("app.hayase", "Metadata Updated");
    String url = call.getString("image");
    if (url != null && !Objects.equals(url, lastUrl)) {
      lastUrl = url;
      BitmapNetworkRequest request = new BitmapNetworkRequest(Request.Method.GET, url, response -> {
        currentImage = response;
        updateSessionMetadata(call);
        call.successCallback(new PluginResult());
      }, error -> Log.e("MediaNotification", "Failed to load image from " + url, error));
      ((MainActivity) this.getBridge().getActivity()).queue.add(request);
    } else {
      updateSessionMetadata(call);
      call.successCallback(new PluginResult());
    }

  }

  private void updateSessionMetadata(PluginCall call) {
    MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
        .putString(METADATA_KEY_TITLE, call.getString("title"))
        .putString(METADATA_KEY_DISPLAY_TITLE, call.getString("title"))
        .putString(METADATA_KEY_DISPLAY_DESCRIPTION, call.getString("description"))
        .putString(METADATA_KEY_ART_URI, call.getString("image"))
        .putLong(METADATA_KEY_DURATION, (long) Math.floor(call.getDouble("duration", 0d) * 1000));

    if (currentImage != null) {
      metadata.putBitmap(METADATA_KEY_ART, currentImage);
    }

    getSession().setMetadata(metadata.build());
  }

  @PluginMethod
  public void setPlaybackState(PluginCall call) {
    MediaSessionCompat session = getSession();
    int state = call.getInt("state", 0);
    long position = (long) Math.floor(call.getDouble("position", 0d) * 1000);
    float rate = call.getFloat("playbackRate", 0f);
    Log.i("app.hayase", "Playstate updated " + state + " " + position + " " + rate);

    session.setPlaybackState(new PlaybackStateCompat.Builder()
        .setState(state, position, rate)
        .setActions(ACTION_PLAY | ACTION_PAUSE | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS | ACTION_STOP
            | ACTION_SEEK_TO | ACTION_FAST_FORWARD | ACTION_REWIND)
        .addCustomAction(
            new PlaybackStateCompat.CustomAction.Builder("enterpictureinpicture", "pip", R.drawable.ic_pip_icon)
                .build())
        .build());
  }

  private class MediaCallback extends MediaSessionCompat.Callback {
    @Override
    public void onCustomAction(String action, Bundle extras) {
      notifyListeners(action, new JSObject());
    }

    @Override
    public void onCommand(String command, Bundle extras, ResultReceiver cb) {
      notifyListeners(command, new JSObject());
    }

    @Override
    public void onPlay() {
      Log.i("app.hayase", "Play event sent");
      notifyListeners("play", new JSObject());
    }

    @Override
    public void onPause() {
      Log.i("app.hayase", "Pause event sent");
      notifyListeners("pause", new JSObject());
    }

    @Override
    public void onSkipToNext() {
      notifyListeners("nexttrack", new JSObject());
    }

    @Override
    public void onSkipToPrevious() {
      notifyListeners("previoustrack", new JSObject());
    }

    @Override
    public void onStop() {
      notifyListeners("stop", new JSObject());
    }

    @Override
    public void onSeekTo(long pos) {
      JSObject ret = new JSObject();
      ret.put("seekTime", (pos / 1000d));
      ret.put("fastSeek", true);
      notifyListeners("seekto", ret);
    }

    @Override
    public void onFastForward() {
      JSObject ret = new JSObject();
      ret.put("seekOffset", 10);
      notifyListeners("seekforward", ret);
    }

    @Override
    public void onRewind() {
      JSObject ret = new JSObject();
      ret.put("seekOffset", 10);
      notifyListeners("seekbackward", ret);
    }
  }
}
