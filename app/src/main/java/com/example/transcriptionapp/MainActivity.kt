package com.example.transcriptionapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.transcriptionapp.ui.theme.TranscriptionAppTheme
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TranscriptionAppTheme {
                SpeechRecognitionScreen()
            }
        }
    }
}

@Composable
fun SpeechRecognitionScreen() {
    var resultText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var modelLoaded by remember { mutableStateOf(false) }
    var speechService: SpeechService? by remember { mutableStateOf(null) }
    var recognitionListener: RecognitionListener? by remember { mutableStateOf(null) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            resultText = "Permission Denied!"
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(context) {
        try {
            val model = Model(loadModel(context)) // Check the logs to see the copied path
            val recognizer = Recognizer(model, 16000.0f)
            recognizer.setPartialWords(true)


            recognitionListener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {
                    resultText = hypothesis
                }

                override fun onResult(hypothesis: String) {
                    resultText = hypothesis
                    Log.d("Result", hypothesis)
                    saveToFile(hypothesis, context)
                }

                override fun onFinalResult(hypothesis: String) {
                    resultText = hypothesis
                    saveToFile(hypothesis, context)
                    isListening = false
                }

                override fun onError(exception: Exception) {
                    resultText = "Error: ${exception.message}"
                    isListening = false
                }

                override fun onTimeout() {
                    isListening = false
                }
            }

            speechService = SpeechService(recognizer, 16000.0f)
            modelLoaded = true
        } catch (e: Exception) {
            resultText = "Failed to load model!"
            Log.e("SpeechRecognition", "Model loading failed", e)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = resultText, modifier = Modifier.padding(16.dp))
            Spacer(modifier = Modifier.height(16.dp))

            if (modelLoaded) {
                Button(
                    onClick = {
                        if (isListening) {
                            speechService?.stop()
                            isListening = false
                        } else {
                            recognitionListener?.let { listener ->
                                speechService?.startListening(listener)
                                isListening = true
                            }
                        }
                    }
                ) {
                    Text(if (isListening) "Stop Listening" else "Start Listening")
                }
            } else {
                Text("Loading model... Please wait")
            }
        }
    }
}

fun loadModel(context: Context): String {
    val assetPath = "model"
    val outputDir = File(context.filesDir, assetPath)

    // Delete the old model directory if it exists
    if (outputDir.exists()) {
        deleteRecursive(outputDir)
    }

    // Create the model directory again
    outputDir.mkdirs()

    // Copy files from assets to the model directory
    copyDirectory(context, assetPath, outputDir)

    Log.d("ModelLoading", "Model files copied to: ${outputDir.absolutePath}")
    return outputDir.absolutePath
}

// Recursive copy from assets to output directory
fun copyDirectory(context: Context, assetDirPath: String, destDir: File) {
    val assetList = context.assets.list(assetDirPath) ?: return

    // Create the destination directory if it doesn't exist
    if (!destDir.exists()) {
        destDir.mkdirs()
    }

    for (fileName in assetList) {
        Log.d("ModelLoading", fileName)
        val assetFilePath = "$assetDirPath/$fileName"
        val destFile = File(destDir, fileName)

        if (context.assets.list(assetFilePath)?.isNotEmpty() == true) {
            // If the file is a directory, copy it recursively
            copyDirectory(context, assetFilePath, destFile)
        } else {
            // Otherwise, copy the file
            context.assets.open(assetFilePath).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }
}

// Helper function to delete a directory and its contents recursively
fun deleteRecursive(fileOrDirectory: File) {
    if (fileOrDirectory.isDirectory) {
        val children = fileOrDirectory.listFiles()
        if (children != null) {
            for (child in children) {
                deleteRecursive(child)
            }
        }
    }
    fileOrDirectory.delete()
}

// Function to save transcription results to a file
fun saveToFile(text: String, context: Context) {
    val file = File(context.getExternalFilesDir(null), "transcription.txt")
    file.appendText("$text\n")
}
