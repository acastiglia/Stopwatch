package com.anthonycastiglia.stopwatch;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StopwatchActivity extends AppCompatActivity {

  private static final long UI_UPDATE_INTERVAL_MS = 10;

  private StopwatchService service;
  private TextView timeDisplay;
  private Button startStopButton;
  private Button lapResetButton;

  private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
  private ScheduledFuture<?> scheduledFuture;

  private ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
      service = ((StopwatchService.StopwatchBinder) iBinder).getService();

      startStopButton.setText(service.timerRunning() ? R.string.stop : R.string.start);
      startStopButton.setEnabled(true);

      lapResetButton.setText(service.timerRunning() ? R.string.lap : R.string.reset);
      lapResetButton.setEnabled(true);

      setDisplayTime(service.getTimeElapsed());
      if (service.timerRunning()) {
        resumeDisplayUpdates();
      }
      service.stopForeground(true);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
      pauseDisplayUpdates();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_stopwatch);

    startService(new Intent(StopwatchActivity.this, StopwatchService.class));

    timeDisplay = (TextView) findViewById(R.id.display_time);

    startStopButton = (Button) findViewById(R.id.button_start_stop);
    lapResetButton = (Button) findViewById(R.id.button_lap_reset);

    startStopButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (service.timerRunning()) {
          service.stopTimer();
          startStopButton.setText(R.string.start);
          lapResetButton.setText(R.string.reset);
          pauseDisplayUpdates();
        } else {
          service.startTimer();
          startStopButton.setText(R.string.stop);
          lapResetButton.setText(R.string.lap);
          resumeDisplayUpdates();
        }
      }
    });

    lapResetButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (service.timerRunning()) {
          Log.i("StopwatchService", "Lap @ " + service.getTimeElapsed());
        } else {
          service.resetTimer();
          setDisplayTime(0);
        }
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    bindService(new Intent(this, StopwatchService.class), connection, Context.BIND_AUTO_CREATE);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (service.timerRunning() || service.getTimeElapsed() > 0) {
      service.startForegroundNotification();
    }
    unbindService(connection);
  }

  private void pauseDisplayUpdates() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
    }
  }

  private void resumeDisplayUpdates() {
    scheduledFuture = executorService.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            setDisplayTime(service.getTimeElapsed());
          }
        });
      }
    }, 0, UI_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  private void setDisplayTime(long timeElapsed) {
    long minutes = TimeUnit.MINUTES.convert(timeElapsed, TimeUnit.MILLISECONDS);
    long seconds = TimeUnit.SECONDS.convert(timeElapsed - TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES), TimeUnit.MILLISECONDS);
    long milliseconds = timeElapsed - TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES) - TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS);

    timeDisplay.setText(String.format(Locale.getDefault(), "%d:%02d.%02d", minutes, seconds, milliseconds / 10));
  }
}
