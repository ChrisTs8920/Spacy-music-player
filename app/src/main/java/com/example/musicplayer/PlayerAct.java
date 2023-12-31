package com.example.musicplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class PlayerAct extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener{

    TextView songTitleText;
    TextView songDuration;
    TextView songTimer;
    ImageButton playBtn;
    ImageButton prevBtn;
    ImageButton nextBtn;
    SeekBar seekBar;
    File currSongFile;
    int currSongId;
    Timer timer = null;
    TimerTask timerTask = null;
    ArrayList<File> filenames = null;
    boolean connected;
    MusicService musicService;
    Intent musicInt;
    SingletonCurr singletonCurr;

    @Override
    protected void onCreate(Bundle SavedInstanceState) {
        super.onCreate(SavedInstanceState);
        this.setContentView(R.layout.playerlayout);
        this.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        songTitleText = findViewById(R.id.songTitleplayer);
        songDuration = findViewById(R.id.songDuration);
        playBtn = findViewById(R.id.playBtn);
        prevBtn = findViewById(R.id.prevBtn);
        nextBtn = findViewById(R.id.nextBtn);
        seekBar = findViewById(R.id.seekBar);
        songTimer = findViewById(R.id.songTimer);
        playBtn.setOnClickListener(this);
        prevBtn.setOnClickListener(this);
        nextBtn.setOnClickListener(this);
        seekBar.setOnSeekBarChangeListener(this);

        filenames = new ArrayList<>();
        connected = false;
        singletonCurr = SingletonCurr.getInstance();
        // get data from previous Activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            currSongFile = (File) extras.get("currFile");
            filenames = (ArrayList<File>) extras.get("files");
        }
        if (filenames != null) {
            currSongId = filenames.indexOf(currSongFile); //set current song index
        }
        updateTitleText();
        doStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(receiver, new IntentFilter("UPDATE"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance (getApplicationContext()). unregisterReceiver (receiver);
        doStop();
    }

    @Override
    public void onClick(View view) {
        if (view == playBtn && !musicService.active) {
            doPlay();
        } else if (view == playBtn){
            doPause();
        }
        if (view == nextBtn) {
            doNext();
        }
        if (view == prevBtn) {
            doPrevious();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        singletonCurr.setCurrSongString(currSongFile.getName());
        timer.cancel();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (musicService.MP != null && fromUser) {
            musicService.MP.seekTo(progress);
            // update text while User is moving the dot of the seek bar
            long sec = (progress / 1000) % 60;
            long min = (progress / 1000) / 60;
            songTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    public void doStart() {
        if (!connected) {
            musicInt = new Intent(this, MusicService.class);
            musicInt.putExtra("currSong", currSongId);
            startService(musicInt); // calls onStartCommand().
            bindService(musicInt, serviceConnection, Context.BIND_AUTO_CREATE); // calls onBind
        }
    }

    public void doStop() {
        if (connected) {
            connected = false;
            unbindService(serviceConnection);
        }
    }

    public void doPlay() {
        if (!connected) {
            return;
        }
        musicService.playSong();
        playBtn.setImageResource(R.drawable.outline_pause_white_36);
    }

    public void doPause() {
        if (!connected) {
            return;
        }
        musicService.pauseSong();
        playBtn.setImageResource(R.drawable.baseline_play_arrow_white_36);
    }

    public void doNext() {
        if (!connected) {
            return;
        }
        currSongFile = musicService.nextSong();
        // update immediately, so that it does not wait for next updateSongTimer call.
        updateUi();
    }

    public void doPrevious() {
        if (!connected) {
            return;
        }
        currSongFile = musicService.previousSong();
        // update immediately, so that it does not wait for next updateSongTimer call.
        updateUi();
    }

    public void updateUi() {
        updateTitleText();
        updateDurationText();
        seekBar.setProgress(0);
        songTimer.setText("00:00");
        seekBar.setMax(musicService.MP.getDuration());
    }

    public void updateTitleText() {
        songTitleText.setText(singletonCurr.getCurrSongString());
    }

    public void updateSongTimer() {
        timerTask = new TimerTask() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long ms = musicService.MP.getCurrentPosition();
                        long sec = (ms / 1000) % 60;
                        long min = (ms / 1000) / 60;
                        songTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
                        seekBar.setProgress((int) ms);
                    }
                });
            }
        };
        if (timer == null) {
            timer = new Timer();
        }
        timer.schedule(timerTask, 0, 1000); // executes every second
    }

    public void updateDurationText() {
        long ms = musicService.MP.getDuration();
        long sec = (ms / 1000) % 60;
        long min = (ms / 1000) / 60;
        songDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) iBinder;
            musicService = binder.getService();
            updateDurationText();
            updateSongTimer();
            if (musicService.MP.isPlaying()) {
                playBtn.setImageResource(R.drawable.outline_pause_white_36);
            } else {
                playBtn.setImageResource(R.drawable.baseline_play_arrow_white_36);
            }
            seekBar.setMax(musicService.MP.getDuration());
            connected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            connected = false;
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int play = intent.getIntExtra("Play", 2);
            if (play == 1) {
                playBtn.setImageResource(R.drawable.outline_pause_white_36);
            } else if (play == 0) {
                playBtn.setImageResource(R.drawable.baseline_play_arrow_white_36);
            }
            updateUi();
        }
    };
}
