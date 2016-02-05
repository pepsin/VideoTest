package com.me.pepsin.videotest.videotest;

import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.VideoView;

import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.builder.Mp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.tracks.ClippedTrack;
import org.mp4parser.muxer.tracks.CroppedTrack;
import org.mp4parser.tools.Mp4Arrays;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, SurfaceHolder.Callback {

    static final int REQUEST_VIDEO_CAPTURE = 1;
    static String LOG_TAG = "libvideo";
    MediaRecorder recorder;
    SurfaceHolder holder;
    boolean recording = false;
    VideoView mVideoView;
    SurfaceView cameraView;

    public static long readUInt64(ByteBuffer byteBuffer) {
        long result = 0;
        // thanks to Erik Nicolas for finding a bug! Cast to long is definitivly needed
        result += readUInt32(byteBuffer) << 32;
        if (result < 0) {
            throw new RuntimeException("I don't know how to deal with UInt64! long is not sufficient and I don't want to use BigInt");
        }
        result += readUInt32(byteBuffer);

        return result;
    }

    public static long readUInt32(ByteBuffer bb) {
        long i = bb.getInt();
        if (i < 0) {
            i += 1l << 32;
        }
        return i;
    }

    public static String read4cc(ByteBuffer bb) {
        byte[] codeBytes = new byte[4];
        bb.get(codeBytes);

        try {
            return new String(codeBytes, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void readMP4Test() throws IOException {
        String path = "/sdcard/videocapture_example.mp4";
        File file = new File(path);
        FileInputStream stream = new FileInputStream(file);
        ReadableByteChannel byteChannel = stream.getChannel();
        ByteBuffer header = ByteBuffer.allocate(32);

        int bytesRead = 0;
        byteChannel.read(header);

        Log.d("libvideo", "Byte read:" + bytesRead);
        header.rewind();

        long size = readUInt32(header);
        // do plausibility check
        if (size < 8 && size > 1) {
            return;
        }


        String type = read4cc(header);
        Log.d("libvideo", "Type:" + type);

        byte[] usertype = null;
        long contentSize;
        Log.d("libvideo", "Size:" + size);
        if (size == 1) {
            header.limit(16);
            byteChannel.read(header);
            header.position(8);
            size = readUInt64(header);
            contentSize = size - 16;
            Log.d("libvideo", "1: Content Size:" + contentSize);
        } else if (size == 0) {
            throw new RuntimeException("box size of zero means 'till end of file. That is not yet supported");
        } else {
            contentSize = size - 8;
            Log.d("libvideo", "2: Content Size:" + contentSize);
        }
        if ("uuid".equals(type)) {
            header.limit(header.limit() + 16);
            byteChannel.read(header);
            usertype = new byte[16];
            for (int i = header.position() - 16; i < header.position(); i++) {
                usertype[i - (header.position() - 16)] = header.get(i);
            }
            contentSize -= 16;
            Log.d("libvideo", "3: Content Size:" + contentSize);
        }

        Log.d("libvideo", "Content Size:" + contentSize);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recorder = new MediaRecorder();
        initRecorder();
        setContentView(R.layout.activity_main);

        cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        holder = cameraView.getHolder();
        holder.addCallback(this);

        mVideoView = (VideoView) findViewById(R.id.videoView);
        findViewById(R.id.shoot_btn).setOnClickListener(this);
        try {
            readMP4Test();
        } catch (IOException e) {
            Log.d(LOG_TAG, "Failed");
        }

    }

    private void initRecorder() {
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

        CamcorderProfile cpHigh = CamcorderProfile
                .get(CamcorderProfile.QUALITY_HIGH);
        recorder.setProfile(cpHigh);
        recorder.setOutputFile("/sdcard/videocapture_example.mp4");
        recorder.setMaxDuration(2000); // 50 seconds
        recorder.setMaxFileSize(5000000); // Approximately 5 megabytes
    }

    private void prepareRecorder() {
        recorder.setPreviewDisplay(holder.getSurface());

        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            finish();
        } catch (IOException e) {
            e.printStackTrace();
            finish();
        }
    }

    public void onClick(View v) {
        try {
            readMP4Test();
        } catch (IOException e) {

        }
        if (recording) {
            recorder.stop();
            recording = false;

//            mVideoView.setVideoPath("/sdcard/final.mp4");
            try {
                reverseVideo("/sdcard/final.mp4");
            } catch (IOException e) {
                Log.e(LOG_TAG, "Reverse Exception:" + e);
            }
            mVideoView.start();

            initRecorder();
            prepareRecorder();
        } else {
            recording = true;
            cameraView.setVisibility(View.VISIBLE);
            recorder.start();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        prepareRecorder();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (recording) {
            recorder.stop();
            recording = false;
        }
        recorder.release();
        finish();
    }

    private void reverseVideo(String path) throws IOException {
        Movie m = MovieCreator.build(path);
        Track videoTrack = null;
        for (Track track : m.getTracks()) {
            if ("vide".equals(track.getHandler())) {
                videoTrack = track;
            }
        }
        if (videoTrack == null) {
            throw new RuntimeException("You need a video track!");
        }
        int refNumSamples = videoTrack.getSamples().size();
        long[] refSampleDuration = videoTrack.getSampleDurations();
        long[] syncSamples = videoTrack.getSyncSamples();
        long[] syncSampleTimes = new long[0];
        int refIndex = 0;
        long refTime = 0;
        for (long syncSample : syncSamples) {
            while (refIndex < syncSample - 1 && refIndex < refNumSamples) {
                refTime += (long) refSampleDuration[refIndex] / videoTrack.getTrackMetaData().getTimescale();
                refIndex++;
            }
            syncSampleTimes = Mp4Arrays.copyOfAndAppend(syncSampleTimes, refTime);

        }
        Map<Track, List<Track>> tracks = new HashMap<Track, List<Track>>();
        for (Track track : m.getTracks()) {
            List<Track> chops = new ArrayList<Track>();

            int lastStart = 0;
            int index = 0;
            int numSamples = track.getSamples().size();
            long[] durations = track.getSampleDurations();
            double time = 0;
            int timeIndex = 0;

            while (index < numSamples) {
                if (timeIndex >= syncSampleTimes.length) {
                    chops.add(new ClippedTrack(track, lastStart, numSamples));
                    System.err.println("Added partial track for " + track.getTrackMetaData().getTrackId() + " from sample " + lastStart + " to " + numSamples);
                    break;
                }
                if (time >= syncSampleTimes[timeIndex]) {
                    if (lastStart != index) {
                        chops.add(new ClippedTrack(track, lastStart, index));
                        System.err.println("Added partial track for " + track.getTrackMetaData().getTrackId() + " from sample " + lastStart + " to " + index);
                        lastStart = index;
                    }
                    timeIndex++;
                }
                time += (double) durations[index] / track.getTrackMetaData().getTimescale();
                index++;

            }
            if (chops.size() > 0) {
                tracks.put(track, chops);
            }
        }
        Mp4Builder b = new DefaultMp4Builder();

        for (int i = 0; i < syncSamples.length; i++) {
            Movie chopped = new Movie();

            for (Track track : tracks.keySet()) {
                chopped.addTrack(tracks.get(track).get(i));
            }

            b.build(chopped).writeContainer(new FileOutputStream("/sdcard/out-" + i + ".mp4").getChannel());
        }
    }
}
