package app.hayase;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import java.util.Objects;

public class MediaNotificationService extends Service {
  private NotificationCompat.Style style = null;
  private MediaControllerCompat controller;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null)
      return START_STICKY;
    if (Objects.equals(intent.getAction(), "app.hayase.action.PIP")) {
      if (controller != null) {
        controller.sendCommand("enterpictureinpicture", null, null);
      }
      return START_STICKY;
    } else if (Objects.equals(intent.getAction(), "android.intent.action.MEDIA_BUTTON")) {
      if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()) || !intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
        return START_STICKY;
      }
      KeyEvent ke = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      if (controller != null) {
        controller.dispatchMediaButtonEvent(ke);
      }
      return START_STICKY;
    } else if (!intent.hasExtra("token")) {
      return START_STICKY;
    }

    MediaSessionCompat.Token token;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      token = intent.getParcelableExtra("token", MediaSessionCompat.Token.class);
    } else {
      token = intent.getParcelableExtra("token");
    }

    assert token != null;

    controller = new MediaControllerCompat(this, token);
    controller.registerCallback(new NotificationCallbackHandler());

    style = new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(token);

    NotificationChannelCompat channel = new NotificationChannelCompat.Builder("playback",
        NotificationManager.IMPORTANCE_LOW)
        .setName("Playback")
        .setDescription("Media Playback Notifications")
        .build();

    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
    notificationManager.createNotificationChannel(channel);

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(2, generateNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
      } else {
        startForeground(2, generateNotification());
      }
      Log.i("MediaNotification", "Created notification");
    } catch (Exception e) {
      Log.e("MediaNotification", "Failed to start foreground, continuing without notification", e);
    }

    return START_STICKY;
  }

  private Notification generateNotification() {
    MediaMetadataCompat metadata = controller.getMetadata();

    Intent pipIntent = new Intent(this, MediaNotificationService.class);
    pipIntent.setAction("app.hayase.action.PIP");
    PendingIntent pipPendingIntent = PendingIntent.getService(this, 1, pipIntent, PendingIntent.FLAG_IMMUTABLE);

    Intent openIntent = new Intent(this, MainActivity.class);
    PendingIntent openPendingIntent = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder notification = new NotificationCompat.Builder(this, "playback")
        .setSmallIcon(R.mipmap.ic_launcher_foreground)
        .setContentTitle(metadata == null ? "Unknown" : metadata.getDescription().getTitle())
        .setContentText(metadata == null ? "Unknown" : metadata.getDescription().getDescription())
        .setContentIntent(openPendingIntent)
        .setStyle(style);

    notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, "Last Track",
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));
    notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_rew, "Rewind",
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)));
    if (controller.getPlaybackState() != null) {
      if (controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
        notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)));
      } else {
        notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, "Play",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)));
      }
    }
    notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_ff, "Fast Forward",
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)));
    notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, "Next Track",
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));
    notification
        .addAction(new NotificationCompat.Action(R.drawable.ic_pip_icon, "Enter PiP", pipPendingIntent));

    if (metadata != null && metadata.getDescription().getIconBitmap() != null) {
      notification.setLargeIcon(metadata.getDescription().getIconBitmap());
    }

    return notification.build();
  }

  private void updateNotification() {
    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
    notificationManager.notify(2, generateNotification());
  }

  @Override
  public void onDestroy() {
    Log.i("MediaNotification", "Stopping notification");
    stopForeground(true);
    stopSelf();
  }

  private class NotificationCallbackHandler extends MediaControllerCompat.Callback {
    private int lastState = 0;

    @Override
    public void onMetadataChanged(@Nullable MediaMetadataCompat metadata) {
      updateNotification();
    }

    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackStateCompat state) {
      if (state == null)
        return;
      if (state.getState() != lastState) {
        lastState = state.getState();
        updateNotification();
      }
    }
  }

}
