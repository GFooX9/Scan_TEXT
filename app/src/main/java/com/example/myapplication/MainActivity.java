package com.example.myapplication;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private EditText etRecognizedText; // Переведено на EditText
    private Button btnGallery, btnCamera, btnCopy;

    private Uri cameraImageUri;
    private TextRecognizer textRecognizer;

    // Использование PhotoPicker Android 13 напрямую к ML Kit
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    imageView.setImageURI(uri);
                    recognizeText(uri);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    imageView.setImageURI(cameraImageUri);
                    recognizeText(cameraImageUri);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imageView = findViewById(R.id.imageView);
        etRecognizedText = findViewById(R.id.etRecognizedText);
        btnGallery = findViewById(R.id.btnGallery);
        btnCamera = findViewById(R.id.btnCamera);
        btnCopy = findViewById(R.id.btnCopy);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        btnGallery.setOnClickListener(v -> {
            galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        btnCopy.setOnClickListener(v -> {
            // Берем текущий текст из поля (включая все ручные правки пользователя)
            String textToCopy = etRecognizedText.getText().toString();
            if (!textToCopy.isEmpty() && !textToCopy.equals("Здесь будет отображен текст...")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Recognized Text", textToCopy);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(MainActivity.this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openCamera() {
        try {
            File tempFile = File.createTempFile("captured_photo", ".jpg", getCacheDir());
            tempFile.deleteOnExit();
            cameraImageUri = FileProvider.getUriForFile(MainActivity.this,
                    getPackageName() + ".provider", tempFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Error creating photo file", Toast.LENGTH_SHORT).show();
        }
    }

    private void recognizeText(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            etRecognizedText.setText("Scanning text...");

            textRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        if (!visionText.getText().trim().isEmpty()) {
                            etRecognizedText.setText(visionText.getText());
                        } else {
                            etRecognizedText.setText("No text found on this image.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        etRecognizedText.setText("Recognition error: " + e.getLocalizedMessage());
                    });
        } catch (IOException e) {
            etRecognizedText.setText("File load error: " + e.getLocalizedMessage());
        }
    }
}
