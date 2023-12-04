package com.example.cameraxvideorecorder;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.database.Cursor;
import android.net.Uri;



import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.RequestBody;
//import okhttp3.Response;

import okhttp3.MultipartBody;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.Response;
import android.os.Vibrator;

import org.w3c.dom.Text;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private TextToSpeech textToSpeech;
    ExecutorService service;
    Recording recording = null;
    VideoCapture<Recorder> videoCapture = null;
    ImageButton capture, toggleFlash, flipCamera;
    PreviewView previewView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;

    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera(cameraFacing);
        }

    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.viewFinder);
        capture = findViewById(R.id.capture);
        toggleFlash = findViewById(R.id.toggleFlash);
        flipCamera = findViewById(R.id.flipCamera);
        capture.setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                activityResultLauncher.launch(Manifest.permission.CAMERA);
            } else if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                activityResultLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activityResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                captureVideo();
            }
        });

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(Manifest.permission.CAMERA);
        } else {
            startCamera(cameraFacing);
        }

        flipCamera.setOnClickListener(view -> {
            if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                cameraFacing = CameraSelector.LENS_FACING_FRONT;
            } else {
                cameraFacing = CameraSelector.LENS_FACING_BACK;
            }
            startCamera(cameraFacing);
        });

        service = Executors.newSingleThreadExecutor();

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.getDefault());

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(MainActivity.this, "Language not supported", Toast.LENGTH_SHORT).show();
                } else {
                    // Read aloud a hardcoded message when the MainActivity is created
//                    readAloud("Hello, this is a hardcoded message.");
                }
            } else {
                Toast.makeText(MainActivity.this, "Text to Speech initialization failed", Toast.LENGTH_SHORT).show();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    // Utterance started
                }

                @Override
                public void onDone(String utteranceId) {
                    // Utterance completed
                }

                @Override
                public void onError(String utteranceId) {
                    // Utterance encountered an error
                }
            });
        }

    }

    private void readAloud(String message) {
        String utteranceId = "readAloudUtterance";
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }
//send frames to server; somehow

    private String sendVideoFileToServer(String videoFilePath) {

        // Create Retrofit instance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://146.190.175.179:3000/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // Create service interface
        ApiService apiService = retrofit.create(ApiService.class);

        // Create video file as RequestBody
        File videoFile = new File(videoFilePath);
//        RequestBody requestFile = okhttp3.RequestBody.create(okhttp3.MediaType.parse("video/mp4"), videoFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("video", videoFile.getName(), RequestBody.create(MediaType.parse("multipart/form-data"),videoFile));

        // Make the API call
        Call<ResponseBody> call = apiService.uploadVideo(body);
        call.enqueue(new Callback<ResponseBody>()
        {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("Server Response", response.toString());
                Log.d("Server Response", response.toString());

                if (response.isSuccessful()) {
                    // Video uploaded successfully
                    Log.d("Upload", "Video uploaded successfully");
                } else {
                    // Handle error response
                    Log.e("Upload", "Error uploading video. Response code: " + response.code());
                }
            }
            //if response is 200 toast sent successfully, else failed

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Handle failure
                Log.e("Upload", "Video upload failed", t);
            }
        });

        return videoFilePath;
    }
    public interface ApiService {
        @Multipart
        @POST("/uploadVideo")
        Call<ResponseBody> uploadVideo(@Part MultipartBody.Part video);
    }


    private String getVideoFilePath(String outputUri) {
        // Convert the content URI to a file path
        String[] projection = {MediaStore.Video.Media.DATA};
        Cursor cursor = getContentResolver().query(Uri.parse(outputUri), projection, null, null, null);
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
        cursor.moveToFirst();
        String videoFilePath = cursor.getString(columnIndex);
        Log.d("FilePath", "videoFilePath found");
        cursor.close();
        return videoFilePath;
    }


    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void sendVideoInBackground(String videoFilePath) {
        Thread thread = new Thread(() -> {
            // Perform the background operation (send video to server)
            String result = sendVideoFileToServer(videoFilePath);

            // Update the UI on the main thread with the result
            mainHandler.post(() -> handleServerResponse(result));
        });

        thread.start();
    }

    private void handleServerResponse(String result) {
        // This method is called on the main (UI) thread
        if (result != null) {
            // Handle the server response (result) here
            // Example: display a Toast with the server response
            Toast.makeText(MainActivity.this, "Good API Connection, video sent successfully", Toast.LENGTH_SHORT).show();
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            readAloud("Video sent successfully");
            Log.d("Server Response", result);
        } else {
            // Handle the case where the server response is null or an error occurred
            Toast.makeText(MainActivity.this, "Error sending video", Toast.LENGTH_SHORT).show();
            readAloud("Error sending video");
        }
    }


    //all video capture ==========================
    public void captureVideo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Request RECORD_AUDIO permission here
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        capture.setImageResource(R.drawable.round_stop_circle_24);
        Recording recording1 = recording;
        if (recording1 != null) {
            recording1.stop();
            recording = null;
            return;
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "VIDEO_" + System.currentTimeMillis());
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/All Seeing Eye");

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        // Start recording the video
        recording = videoCapture.getOutput().prepareRecording(MainActivity.this, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(MainActivity.this), videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                        capture.setEnabled(true);
                        Toast.makeText(this, "Recording Video", Toast.LENGTH_SHORT).show();
                        readAloud("Recording Video");
                    } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        if (!((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                            String outputUri = ((VideoRecordEvent.Finalize) videoRecordEvent).getOutputResults().getOutputUri().toString();
                            Toast.makeText(this, "Video capture succeeded, sending to server", Toast.LENGTH_SHORT).show();
                            readAloud("Video capture succeeded");

                            // Get the recorded video file path
                            String videoFilePath = getVideoFilePath(outputUri);

                            // Send the recorded video
                            sendVideoInBackground(videoFilePath);
                        } else {
                            recording.close();
                            recording = null;
                            String msg = "Error: " + ((VideoRecordEvent.Finalize) videoRecordEvent).getError();
                            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
                        }

                        capture.setImageResource(R.drawable.round_fiber_manual_record_24);
                    }
                });
    }



    public void startCamera(int cameraFacing) {
        Log.d("CameraX", "startCamera: Initializing camera");


        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        ListenableFuture<ProcessCameraProvider> processCameraProvider = ProcessCameraProvider.getInstance(MainActivity.this);

        processCameraProvider.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = processCameraProvider.get();
                if (cameraProvider == null) { //debugging for preview
                    Log.e("CameraX", "Camera provider is null");
                    return;
                }
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                cameraProvider.unbindAll();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing).build();

                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(MainActivity.this));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, continue with capturing video...
                // ...
            } else {
                // Permission denied, show a message or take appropriate action
                Toast.makeText(this, "Permission denied. Cannot capture video.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        service.shutdown();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}

