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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
  private AudioManager audioManager = null;
  private AudioFocusRequest audioFocusRequest = null;
  private boolean hasAudioFocus = false;
  private boolean resumeOnFocusGain = false;
  private int lastPlaybackState = PlaybackStateCompat.STATE_NONE;
  private final AudioManager.OnAudioFocusChangeListener focusChangeListener = this::onAudioFocusChange;

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
    abandonAudioFocus();
    if (current != null) {
      getContext().stopService(new Intent(this.getContext(), MediaNotificationService.class));
      current.release();
      current = null;
    }
  }

  // Take audio focus so Android pauses other apps, the same way a native media
  // player does. The Web Audio output of the custom player does not request
  // focus by itself.
  private void requestAudioFocus() {
    if (hasAudioFocus) {
      return;
    }
    AudioManager manager = getAudioManager();
    if (manager == null) {
      return;
    }

    int result;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (audioFocusRequest == null) {
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build();
        // Transient focus tells other apps to pause and expect a resume. On
        // abandon the framework returns focus to them, so background audio
        // starts again when the player pauses.
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener, new Handler(Looper.getMainLooper()))
            .build();
      }
      result = manager.requestAudioFocus(audioFocusRequest);
    } else {
      result = manager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }

    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    Log.i("app.hayase", "Audio focus requested, granted: " + hasAudioFocus);
  }

  // Release focus so a paused app lets other apps play again.
  private void abandonAudioFocus() {
    if (!hasAudioFocus) {
      return;
    }
    AudioManager manager = getAudioManager();
    if (manager != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
        manager.abandonAudioFocusRequest(audioFocusRequest);
      } else {
        manager.abandonAudioFocus(focusChangeListener);
      }
    }
    hasAudioFocus = false;
    Log.i("app.hayase", "Audio focus abandoned");
  }

  // Pause the player when another app takes focus (a call, navigation or a
  // media app). A transient loss resumes on AUDIOFOCUS_GAIN. The web player
  // receives the commands as MediaSession play/pause actions.
  private void onAudioFocusChange(int focusChange) {
    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_LOSS:
        if (!hasAudioFocus) {
          break;
        }
        hasAudioFocus = false;
        resumeOnFocusGain = false;
        notifyListeners("pause", new JSObject());
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        if (!hasAudioFocus) {
          break;
        }
        hasAudioFocus = false;
        resumeOnFocusGain = true;
        notifyListeners("pause", new JSObject());
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
        // Keep playing. Ducking the Web Audio output is the player's job.
        break;
      case AudioManager.AUDIOFOCUS_GAIN:
        hasAudioFocus = true;
        if (resumeOnFocusGain) {
          resumeOnFocusGain = false;
          notifyListeners("play", new JSObject());
        }
        break;
    }
  }

  private AudioManager getAudioManager() {
    if (audioManager == null) {
      audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }
    return audioManager;
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

    // Only act on a state transition. Periodic position updates repeat the
    // playing state, and re-requesting focus there would steal it back from the
    // app that just took it.
    if (state != lastPlaybackState) {
      if (state == PlaybackStateCompat.STATE_PLAYING) {
        requestAudioFocus();
      } else if (state == PlaybackStateCompat.STATE_PAUSED
          || state == PlaybackStateCompat.STATE_STOPPED
          || state == PlaybackStateCompat.STATE_NONE) {
        abandonAudioFocus();
      }
      lastPlaybackState = state;
    }
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
