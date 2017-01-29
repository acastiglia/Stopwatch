package com.anthonycastiglia.stopwatch;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StopwatchService extends Service {

  private static final int ONGOING_NOTIFICATION_ID = 1;
  private static final long UPDATE_INTERVAL_MS = 5;

  private ScheduledFuture<?> scheduledFuture;
  private ScheduledFuture<?> notificationUpdates;

  private long lastUpdatedAt;
  private long timeElapsed;

  private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
  private IBinder binder = new StopwatchBinder();
  private boolean isRunning;
  private boolean hasForegroundNotification;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    return true;
  }

  void startForegroundNotification() {
    Intent notificationIntent = new Intent(this, StopwatchActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

    final Notification.Builder notification = new Notification.Builder(this)
        .setContentTitle(getString(R.string.notification_title))
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentIntent(pendingIntent);

    startForeground(ONGOING_NOTIFICATION_ID, notification.build());

    final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    notificationUpdates = executorService.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        notificationManager.notify(ONGOING_NOTIFICATION_ID, notification.setContentText(format(timeElapsed)).build());
      }
    }, 0, 1, TimeUnit.SECONDS);
    hasForegroundNotification = true;
  }

  void stopForegroundNotification() {
    notificationUpdates.cancel(false);
    stopForeground(true);
    hasForegroundNotification = false;
  }

  private String format(long timeElapsed) {
    long minutes = TimeUnit.MINUTES.convert(timeElapsed, TimeUnit.MILLISECONDS);
    long seconds = TimeUnit.SECONDS.convert(timeElapsed - TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES), TimeUnit.MILLISECONDS);
    return String.format(Locale.getDefault(), "%01d:%02d", minutes, seconds);
  }

  @Override
  public void onRebind(Intent intent) {
    super.onRebind(intent);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, final int startId) {
    super.onStartCommand(intent, flags, startId);
    return START_STICKY;
  }

  void startTimer() {
    lastUpdatedAt = System.currentTimeMillis();
    scheduledFuture = executorService.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        updateTimeElapsed();
      }
    }, 0, UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    isRunning = true;
  }

  private void updateTimeElapsed() {
    long now = System.currentTimeMillis();
    timeElapsed += now - lastUpdatedAt;
    lastUpdatedAt = now;
  }

  void stopTimer() {
    updateTimeElapsed();
    scheduledFuture.cancel(false);
    isRunning = false;
  }

  void resetTimer() {
    timeElapsed = 0;
  }

  long getTimeElapsed() {
    return timeElapsed;
  }

  public boolean timerRunning() {
    return isRunning;
  }

  public boolean hasForegroundNotification() {
    return hasForegroundNotification;
  }

  class StopwatchBinder extends Binder {
    StopwatchService getService() {
      return StopwatchService.this;
    }
  }
}
