package com.colynlowrey.aperture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// TEACHING NOTE -- walking skeleton, slice 3: MOOD INPUT.
// What's here beyond slice 2:
//   * MoodMapper: free text -> LookRecipe (the agent's contract; today a
//     keyword scorer, tomorrow an on-device model behind the same API)
//   * a text field above the chips; applied moods get their own chip
// Slices 1-2 (for the record):
//   * LookRecipe: Recipe v1 (default/dusk/noir) ported from the Look Lab
//   * viewfinder filter: GPU ColorMatrix effect (API 31+) so the recipe
//     is visible BEFORE you shoot -- tap chips to compare instantly
//   * capture: raw JPEG -> LookLab.apply(full recipe) -> MediaStore
//     so the saved photo matches what the viewfinder showed (plus the
//     two stages previews can't render: local contrast + grain)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CameraSkeleton()
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun CameraSkeleton() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Which recipe the viewfinder shows and the capture will use.
    var recipe by remember { mutableStateOf(LookRecipe.DEFAULT) }

    // Slice 3: mood text -> recipe. moodRecipe is kept so the user's own
    // chip can be tapped again later; the presets and the mood chip share
    // one selection model (selected = current recipe's name).
    var moodInput by remember { mutableStateOf("") }
    var moodRecipe by remember { mutableStateOf<LookRecipe?>(null) }

    val applyMood = {
        val text = moodInput.trim()
        if (text.isNotEmpty()) {
            val result = MoodMapper.map(text)
            moodRecipe = result.recipe
            recipe = result.recipe
            Toast.makeText(
                context,
                if (result.matched.isEmpty()) {
                    "No mood words recognized -- kept the default look"
                } else {
                    "Mood applied: ${result.matched.joinToString(" + ")}"
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    // COMPATIBLE mode forces TextureView -- required so RenderEffect can
    // filter the preview (SurfaceView lives on its own layer and can't).
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Apply the recipe as a real-time GPU color filter on every preview
    // frame. Changing chips re-runs this -> instant viewfinder update.
    // API 31+ only; below that the preview stays raw (capture still
    // processes, so no photo is ever wrong -- just unpreviews).
    LaunchedEffect(recipe) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            previewView.setRenderEffect(
                RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(recipe.toColorMatrix())
                )
            )
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    if (hasPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Scrim: the controls sit over live video; a soft dark
                    // panel keeps text and chips legible without hiding
                    // the frame above.
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MoodRow(
                    input = moodInput,
                    onInput = { moodInput = it },
                    onApply = applyMood,
                )
                PresetRow(
                    selected = recipe,
                    mood = moodRecipe,
                    onSelect = { recipe = it },
                    modifier = Modifier.padding(top = 10.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(72.dp)
                        .background(Color.White, shape = CircleShape)
                        .clickable {
                            capturePhoto(context, imageCapture, recipe)
                        }
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Aperture needs the camera to do its thing.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera access")
            }
        }
    }
}

// The mood box: describe the shot in your own words, hit Set (or the
// keyboard's Done), and the viewfinder re-tunes. This input is the exact
// slot the on-device agent will occupy later -- same field, smarter
// occupant.
@Composable
fun MoodRow(input: String, onInput: (String) -> Unit, onApply: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.width(250.dp),
            singleLine = true,
            placeholder = { Text("describe the mood\u2026") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onApply() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.92f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.92f),
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
            ),
        )
        Button(onClick = onApply) { Text("Set") }
    }
}

// The mood vocabulary in physical form. The agent replaces this picker
// later -- but the presets are what it will be choosing FROM. When a mood
// is applied, its chip appears first so the user can see and re-select
// their own words.
@Composable
fun PresetRow(
    selected: LookRecipe,
    mood: LookRecipe?,
    onSelect: (LookRecipe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips: List<LookRecipe> = if (mood != null) listOf(mood) + LookRecipe.ALL else LookRecipe.ALL
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { preset ->
            FilterChip(
                selected = preset.name == selected.name,
                onClick = { onSelect(preset) },
                label = { Text(preset.name) },
                shape = RoundedCornerShape(50),
            )
        }
    }
}

// Capture flow: raw JPEG to cache -> full recipe in LookLab -> MediaStore.
// Raw first (never lose a photo to a processing crash), then processed.
private fun capturePhoto(context: Context, imageCapture: ImageCapture, recipe: LookRecipe) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val cacheFile = File(context.cacheDir, "aperture_$timestamp.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(cacheFile).build()

    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                // Heavy work off the main thread; MediaStore write on a
                // background thread too so the UI never hitches.
                Thread {
                    try {
                        val raw = BitmapFactory.decodeFile(cacheFile.absolutePath)
                        // CameraX records rotation as EXIF metadata, not in
                        // the pixels. We re-encode, so bake it in now or the
                        // saved photo comes out sideways.
                        val upright = applyExifOrientation(raw, cacheFile.absolutePath)
                        if (upright !== raw) raw.recycle()
                        val processed = LookLab.apply(upright, recipe)
                        saveToGallery(context, processed, "APERTURE_$timestamp.jpg")
                        upright.recycle()
                        processed.recycle()
                        cacheFile.delete()
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "Saved with '${recipe.name}' look to Pictures/Aperture",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Processing failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        }
    )
}

// TEACHING NOTE -- EXIF rotation: the camera sensor is landscape; the JPEG
// pixels stay landscape and the photo viewer is expected to read the
// Orientation tag and rotate on display. We're decoding to a Bitmap and
// re-encoding (which drops EXIF), so we must physically rotate here.
private fun applyExifOrientation(source: Bitmap, path: String): Bitmap {
    val orientation = try {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return source
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Aperture")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    values.clear()
    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
    context.contentResolver.update(uri, values, null, null)
    return uri
}
