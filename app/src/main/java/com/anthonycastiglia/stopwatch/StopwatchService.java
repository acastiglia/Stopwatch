package com.anthonycastiglia.stopwatch;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StopwatchService extends Service {

  private static final int ONGOING_NOTIFICATION_ID = 1;
  private static final long UPDATE_INTERVAL_MS = 5;

  private ScheduledFuture<?> scheduledFuture;

  private long lastUpdatedAt;
  private long timeElapsed;

  private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
  private IBinder binder = new StopwatchBinder();
  private boolean isRunning;

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

    Notification notification = new Notification.Builder(this)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentIntent(pendingIntent)
        .build();

    startForeground(ONGOING_NOTIFICATION_ID, notification);
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

  class StopwatchBinder extends Binder {
    StopwatchService getService() {
      return StopwatchService.this;
    }
  }
}
