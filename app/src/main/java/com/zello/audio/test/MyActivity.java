package com.zello.audio.test;


import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class MyActivity extends AppCompatActivity {

    // Config
    static int SAMPLE_RATE_IN_HZ = 16000;
    static int NUMBER_OF_SECONDS_TO_RECORD = 30;

    // Views
    private Button togglePlayback;
    private Button toggleRecord;
    private SeekBar playbackPlayhead;
    private SeekBar recordPlayhead;
    private TextView playbackTimecode;
    private TextView recordTimecode;

    // Record Vars
    private AudioRecord recorder;
    private short[] recordedData;
    private int recordIndex = 0;
    private RecordAudioThread recorderThread;

    // Playback Vars
    private short[] playbackData;
    private AudioTrack playback;
    private int playbackIndex = 0;
    private PlayAudioThread playbackThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        String requiredPermission = Manifest.permission.RECORD_AUDIO;
        ActivityCompat.requestPermissions(this, new String[]{requiredPermission}, 0);

        recordTimecode = (TextView)findViewById(R.id.recordTimecode);
        playbackTimecode = (TextView)findViewById(R.id.playbackTimecode);
        toggleRecord = (Button)findViewById(R.id.toggleRecordButton);
        togglePlayback = (Button)findViewById(R.id.togglePlaybackButton);

        recordPlayhead = (SeekBar)findViewById(R.id.recordPlayhead);
        recordPlayhead.setMax(NUMBER_OF_SECONDS_TO_RECORD*SAMPLE_RATE_IN_HZ);
        recordPlayhead.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long timecode = (progress*1000)/SAMPLE_RATE_IN_HZ;
                recordTimecode.setText(formatTime(timecode));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopRecording();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                recordIndex = seekBar.getProgress();
            }
        });

        playbackPlayhead = (SeekBar)findViewById(R.id.playbackPlayhead);
        playbackPlayhead.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long timecode = (progress*1000)/SAMPLE_RATE_IN_HZ;
                playbackTimecode.setText(formatTime(timecode));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopPlaying();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                playbackIndex = seekBar.getProgress();
            }
        });

    }

    @Override
    protected void onDestroy(){
        if (recorder != null) recorder.release();
        if (playbackThread != null) playbackThread.interrupt();
        if (recorderThread != null) recorderThread.interrupt();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults){
        int recordAudioIndex = Arrays.asList(permissions).indexOf(Manifest.permission.RECORD_AUDIO);
        if (recordAudioIndex >= 0){
            if (grantResults[recordAudioIndex] == PackageManager.PERMISSION_GRANTED){
                recorder = createAudioRecord();
                playback = createAudioTrack();

                Log.d(String.valueOf(MyActivity.class), "Recorder Valid: " + String.valueOf(recorder.getState() == AudioRecord.STATE_INITIALIZED));


                Log.d(String.valueOf(MyActivity.class), "Recorder: " + String.valueOf(recorder.getRecordingState() + "Playback: " + String.valueOf(playback.getPlayState())));

            }
            else{
                Log.d(String.valueOf(MyActivity.class), "Permissions: Audio Recording Denied");
            }
        }

    }

    private String formatTime(long milliseconds){
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)-TimeUnit.MINUTES.toSeconds(minutes);
        long millis = (milliseconds - TimeUnit.SECONDS.toMillis(seconds))/100;
        return String.format("%02d:%02d.%d", minutes, seconds, millis);
    }

    private AudioTrack createAudioTrack(){
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE_IN_HZ,channelConfig,audioFormat);
        int mode = AudioTrack.MODE_STREAM;
        int streamType = AudioManager.USE_DEFAULT_STREAM_TYPE;

        AudioTrack playback = new AudioTrack (streamType, SAMPLE_RATE_IN_HZ, channelConfig, audioFormat, bufferSizeInBytes, mode);
        return playback;
    }

    private AudioRecord createAudioRecord(){
        int numberOfSeconds = NUMBER_OF_SECONDS_TO_RECORD;
        int audioSource = MediaRecorder.AudioSource.MIC;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, channelConfig, audioFormat);

        AudioRecord recorder = new AudioRecord(audioSource, SAMPLE_RATE_IN_HZ, channelConfig, audioFormat, bufferSizeInBytes);
        recordedData = new short[SAMPLE_RATE_IN_HZ*numberOfSeconds];
        recordPlayhead.setMax(recordedData.length);
        playbackPlayhead.setMax(recordedData.length);

        Log.d(String.valueOf(MyActivity.class), "recordedData.length:" + String.valueOf(recordedData.length));

        return recorder;
    }

    public void toggleRecord(View view) {
        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            stopRecording();
        }
        else {
            startRecording();
        }
    }

    public void togglePlayback(View view) {
        if (playback.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            stopPlaying();
        }
        else {
            startPlaying();
        }
    }

    private void startRecording(){

        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            stopRecording();
        }

        toggleRecord.setText(getResources().getString(R.string.record_button_stop));

        recorderThread = new RecordAudioThread();
        recorderThread.start();
    }

    private void stopRecording() {
        toggleRecord.setText(getResources().getString(R.string.record_button_start));

        if (recorder != null) {
            recorder.stop();
        }
        if (recorderThread != null){
            recorderThread.interrupt();
        }

    }

    private void startPlaying(){
        togglePlayback.setText(getResources().getString(R.string.play_button_stop));

        if (playback.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            stopPlaying();
        }

        playbackData = recordedData.clone();
        playbackThread = new PlayAudioThread();
        playbackThread.start();
    }

    private void stopPlaying(){
        togglePlayback.setText(getResources().getString(R.string.play_button_start));

        if (playback != null) {
            playback.pause();
        }
        if (playbackThread != null){
            playbackThread.interrupt();
        }
    }

    class RecordAudioThread extends Thread {

        @Override
        public void run() {
            Log.d(String.valueOf(MyActivity.class), "RecordAudioThread");
            recorder.startRecording();
            Log.d(String.valueOf(MyActivity.class), "recorder.startRecording()");

            int buffer = 0;
            while (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING){
                buffer = recorder.getBufferSizeInFrames();
                if (recordIndex + buffer > recordedData.length){
                    buffer = recordedData.length - recordIndex;
                }
                recordIndex += recorder.read(recordedData, recordIndex, buffer);
                if (recordIndex >= recordedData.length){
                    recordIndex = 0;
                }
                publishProgress();
            }

            recorder.stop();
        }

        protected void publishProgress() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recordPlayhead.setProgress(recordIndex);

                }
            });
        }
    }

    class PlayAudioThread extends Thread{
        @Override
        public void run() {
            Log.d(String.valueOf(MyActivity.class), "PlayAudioThread");
            playback.play();
            Log.d(String.valueOf(MyActivity.class), "playback.play");

            int buffer = 0;
            while (playback.getPlayState() == AudioTrack.PLAYSTATE_PLAYING){
                buffer = playback.getBufferSizeInFrames();
                if (playbackIndex + buffer > recordedData.length){
                    buffer = recordedData.length - playbackIndex;
                }
                playbackIndex += playback.write(playbackData, playbackIndex, buffer, AudioTrack.WRITE_NON_BLOCKING);
                if(playbackIndex >= playbackData.length){
                    playbackIndex = 0;
                }
                publishProgress();
            }
        }

        protected void publishProgress() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playbackPlayhead.setProgress(playbackIndex);
                }
            });
        }
    }

//    class RecordAudioThread extends Thread {
//
//        @Override
//        public void run() {
//            Log.d(String.valueOf(MyActivity.class), "RecordAudioThread");
//
//            int bufferSize = recorder.getBufferSizeInFrames();
//            short[] audioData = new short[bufferSize];
//
//            recorder.startRecording();
//            Log.d(String.valueOf(MyActivity.class), "recorder.startRecording()");
//
//            while (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
//            {
//                recorder.read(audioData, 0, bufferSize);
//
//                for (int i=0; i<audioData.length; i++){
//                    if(recordIndex == recordedData.length){
//                        recordIndex = 0;
//                    }
//                    recordedData[recordIndex] = audioData[i];
//                    recordIndex++;
//                }
//                Log.d(String.valueOf(MyActivity.class), "recordIndex:" + String.valueOf(recordIndex));
//
//                publishProgress();
//            }
//
//            recorder.stop();
//        }
//
//        protected void publishProgress() {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    recordPlayhead.setProgress(recordIndex);
//
//                }
//            });
//        }
//    }
//
//    class PlayAudioThread extends Thread{
//        @Override
//        public void run() {
//            Log.d(String.valueOf(MyActivity.class), "PlayAudioThread");
//            int bufferSize = recorder.getBufferSizeInFrames();
//
//            playback.play();
//            Log.d(String.valueOf(MyActivity.class), "playback.play");
//
//            while (playback.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
//            {
//                if(playbackIndex >= recordedData.length){
//                    playbackIndex = 0;
//                }
//                playbackIndex += playback.write(recordedData, playbackIndex, bufferSize);
//                Log.d(String.valueOf(MyActivity.class), "playbackIndex:" + String.valueOf(playbackIndex));
//
//
//                publishProgress();
//            }
//        }
//
//        protected void publishProgress() {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    playbackPlayhead.setProgress(playbackIndex);
//                }
//            });
//        }
//    }



//    class PlayAudioTask extends AsyncTask<Void, Void, Void>{
//
//        protected Void doInBackground(Void... params) {
//            Log.d(String.valueOf(MyActivity.class), "PlayAudioTask");
//            int bufferSize = playback.getBufferSizeInFrames();
//
//            int offset = 0;
//            playback.play();
//            Log.d(String.valueOf(MyActivity.class), "playback.play()");
//
//            while (playback.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
//            {
//                if(playbackIndex == recordedData.length){
//                    playbackIndex = 0;
//                }
//                playbackIndex += playback.write(recordedData, playbackIndex, bufferSize, AudioTrack.WRITE_NON_BLOCKING);
//
//                publishProgress();
//            }
//            return null;
//        }
//
//        protected void onProgressUpdate(Void... progress) {
//            playbackPlayhead.setProgress(playbackIndex);
//        }
//    }
//
//    private class RecordAudioTask extends AsyncTask<Void, Void, Void> {
//        protected Void doInBackground(Void... params) {
//            Log.d(String.valueOf(MyActivity.class), "RecordAudioTask");
//
//            int bufferSize = recorder.getBufferSizeInFrames();
//            short[] audioData = new short[bufferSize];
//
//            recorder.startRecording();
//            Log.d(String.valueOf(MyActivity.class), "recorder.startRecording()");
//
//            while (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
//            {
//                recorder.read(audioData, 0, bufferSize);
//
//                for (int i=0; i<audioData.length; i++){
//                    if(recordIndex == recordedData.length){
//                        recordIndex = 0;
//                    }
//                    recordedData[recordIndex] = audioData[i];
//                    recordIndex++;
//                }
//
//                publishProgress();
//            }
//
//            recorder.stop();
//            return null;
//        }
//
//        protected void onProgressUpdate(Void... progress) {
//            recordPlayhead.setProgress(recordIndex);
//        }
//    }



}
