package nctu.nol.badmintonlogprogram;

import android.app.Activity;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Vector;

import nctu.nol.algo.CountSpectrum;
import nctu.nol.algo.FrequencyBandModel;
import nctu.nol.algo.PeakDetector;
import nctu.nol.badmintonlogprogram.chart.AudioWaveChart;
import nctu.nol.badmintonlogprogram.chart.SpectrumChart;
import nctu.nol.bt.devices.BeaconHandler;
import nctu.nol.bt.devices.SoundWaveHandler;
import nctu.nol.file.SystemParameters;
import nctu.nol.file.WavReader;

/**
 * Created by Smile on 2016/7/21.
 */
public class ShowTrainingData extends Activity {
    private final static String TAG = ShowTrainingData.class.getSimpleName();

    private LinearLayout chart_audio;
    private LinearLayout chart_fft;
    final AudioWaveChart awc = new AudioWaveChart(ShowTrainingData.this);
    final SpectrumChart sc = new SpectrumChart(ShowTrainingData.this);

    // Extra data
    private String DataPath;
    private long offset;

    // Audio Data
    private double[] audio_time = {};
    private double[] audio_value = {};
    private float[] audio_time_f = {};
    private float[] audio_value_f = {};

    // Peak Data
    private double[] peak_time = {};
    private double[] peak_value = {};
    private int[] peak_idx = {};

    // FFT Data
    private double[] fft_freq = {};
    private double[] fft_value = {};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_training);

        initialViewandEvent();
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            DataPath = extras.getString(DataListPage.EXTRA_PATH);
            offset = extras.getLong(DataListPage.EXTRA_OFFSET);
            Prepare();
        }

        registerReceiver(mChartClickEventReceiver, makeChartClickEventIntentFilter());
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    private void initialViewandEvent(){
        chart_audio = (LinearLayout)findViewById(R.id.chart_whole_audio_wave);
        chart_fft = (LinearLayout) findViewById(R.id.chart_fft_wave);
    }

    private void Prepare(){
        final ProgressDialog dialog = ProgressDialog.show(ShowTrainingData.this, "請稍後", "讀取音訊資料中", true);
        new Thread() {
            @Override
            public void run() {
                HandleAudioData(awc);
                HandlePeakData(awc);
                HandleFreqTrainingBlock(awc);

                if(peak_time.length > 0)
                    HandlerFFTData(sc, peak_idx[0]);

                runOnUiThread(new Runnable() {
                    public void run() {
                        awc.MakeChart(chart_audio);
                        sc.MakeChart(chart_fft);
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }


    private void HandleAudioData(final AudioWaveChart awc){
        SetAudioSamplesByPath(DataPath, offset);
        awc.AddChartDataset(audio_time, audio_value, Color.argb(255, 51, 102, 0));
    }

    private void HandlePeakData(final AudioWaveChart awc){
        // Peak Data
        PeakDetector pd = new PeakDetector(700, 350);
        List<Integer> peaks = pd.findPeakIndex(audio_time_f, audio_value_f, (float)0.35);
        peak_time = new double[peaks.size()];
        peak_value = new double[peaks.size()];
        peak_idx = new int[peaks.size()];
        for(int i = 0; i < peaks.size(); i++){
            int idx = peaks.get(i);
            peak_idx[i] = idx;
            peak_time[i] = audio_time[idx];
            peak_value[i] = audio_value[idx];
        }
        awc.AddChartDataset(peak_time, peak_value, Color.RED);
    }

    private void HandleFreqTrainingBlock(final AudioWaveChart awc){
        for(int i = 0; i < peak_idx.length; i++){
            int idx = peak_idx[i];
            for(int j = 0; j < FrequencyBandModel.WINDOW_NUM; j++){
                double[] block_time = new double[2];
                double[] block_value = new double[2];

                block_time[0] = audio_time[idx];
                block_value[0] = 1;
                block_time[1] = audio_time[idx+512];
                block_value[1] = 1;
                idx += FrequencyBandModel.FFT_LENGTH;
                awc.AddChartDataset(block_time, block_value, Color.argb(60, 255, 0, 0));
            }
        }
    }

    private void HandlerFFTData(final SpectrumChart sc, int start_position){

        //Use fft
        FrequencyBandModel fbm  = new FrequencyBandModel();
        CountSpectrum cs = new CountSpectrum(FrequencyBandModel.FFT_LENGTH);
        Vector<FrequencyBandModel.FreqBand> spec = fbm.getSpectrum(cs, start_position, audio_value_f, SoundWaveHandler.SAMPLE_RATE);

        fft_freq = new double[spec.size()];
        fft_value = new double[spec.size()];
        for(int i = 0; i < spec.size(); i++){
            fft_freq[i] = spec.get(i).Freq;
            fft_value[i] = spec.get(i).Power;
        }
        sc.AddChartDataset(fft_freq, fft_value, Color.BLUE);

    }

    private void SetAudioSamplesByPath(final String path, final long offset) {
        // Read Wav File, store data
        try {
            WavReader wr = new WavReader(new FileInputStream(path + "Sound.wav"));
            short[] samples = wr.getShortSamples();

            audio_time = new double[samples.length];
            audio_value = new double[samples.length];
            audio_time_f = new float[samples.length];
            audio_value_f = new float[samples.length];

            float deltaT = (1 / (float) SoundWaveHandler.SAMPLE_RATE) * 1000;

            for (int i = 0; i < samples.length; i++) {
                audio_time[i] = offset + deltaT * i;
                audio_value[i] = (double)samples[i] / 32768;

                audio_time_f[i] = offset + deltaT * i;
                audio_value_f[i] = (float)samples[i] / 32768;
            }

        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**********************/
    /**    Broadcast Event	 **/
    /**********************/
    private final BroadcastReceiver mChartClickEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if( AudioWaveChart.ACTION_CLICK_EVENT.equals(action) ) {
                double clicked_time = intent.getDoubleExtra(AudioWaveChart.EXTRA_CLICK_POSITION_TIME, 0);

                // check clicked_time is in fft block
                for(int i = 0; i < peak_idx.length; i++) {
                    for (int j = 0; j < FrequencyBandModel.WINDOW_NUM; j++) {
                        int idx = peak_idx[i] + j*FrequencyBandModel.FFT_LENGTH;

                        if(idx+FrequencyBandModel.FFT_LENGTH < audio_time.length
                                && audio_time[idx] <= clicked_time
                                && audio_time[idx+FrequencyBandModel.FFT_LENGTH] > clicked_time){

                            sc.ClearAllDataset();
                            HandlerFFTData(sc, idx);
                            sc.MakeChart(chart_fft);

                            break;
                        }
                    }
                }
            }
        }
    };
    private static IntentFilter makeChartClickEventIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AudioWaveChart.ACTION_CLICK_EVENT);
        return intentFilter;
    }
}
