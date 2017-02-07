package com.anthonycastiglia.stopwatch;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
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

  private RecyclerView lapList;
  private LapAdapter lapAdapter;

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

      if (service.hasForegroundNotification()) {
        service.stopForegroundNotification();
      }

      lapAdapter.setLaps(service.getLaps());
      lapAdapter.notifyDataSetChanged();
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
          service.recordLap();
          lapList.getAdapter().notifyDataSetChanged();
        } else {
          service.reset();
          lapAdapter.notifyDataSetChanged();
          setDisplayTime(0);
        }
      }
    });

    lapList = (RecyclerView) findViewById(R.id.lap_list);
    lapList.setHasFixedSize(true);
    lapList.setLayoutManager(new LinearLayoutManager(this));
    lapAdapter = new LapAdapter();
    lapList.setAdapter(lapAdapter);
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
    timeDisplay.setText(formatTime(timeElapsed));
  }

  private String formatTime(long timeElapsed) {
    long minutes = TimeUnit.MINUTES.convert(timeElapsed, TimeUnit.MILLISECONDS);
    long seconds = TimeUnit.SECONDS.convert(timeElapsed - TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES), TimeUnit.MILLISECONDS);
    long milliseconds = timeElapsed - TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES) - TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS);

    return String.format(Locale.getDefault(), "%d:%02d.%02d", minutes, seconds, milliseconds / 10);
  }

  private class LapHolder extends RecyclerView.ViewHolder {
    private final TextView textView;

    LapHolder(TextView textView) {
      super(textView);
      this.textView = textView;
    }

    void bind(long lapTime) {
      textView.setText(formatTime(lapTime));
    }
  }

  private class LapAdapter extends RecyclerView.Adapter<LapHolder> {

    List<Long> laps;

    LapAdapter() {
      this.laps = Collections.emptyList();
    }

    void setLaps(List<Long> laps) {
      this.laps = laps;
    }

    @Override
    public LapHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(StopwatchActivity.this);
      TextView view = (TextView) inflater.inflate(R.layout.lap_item, parent, false);
      return new LapHolder(view);
    }

    @Override
    public void onBindViewHolder(LapHolder holder, int position) {
      holder.bind(laps.get(laps.size() - 1 - position));
    }

    @Override
    public int getItemCount() {
      return laps.size();
    }
  }
}
