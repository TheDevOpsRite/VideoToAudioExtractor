package com.thedevopsrite.audioforge

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler
import java.io.File

class MainActivity : Activity() {
    private val background = Color.rgb(8, 11, 16)
    private val surface = Color.rgb(23, 29, 34)
    private val surfaceLight = Color.rgb(32, 41, 48)
    private val text = Color.rgb(244, 247, 248)
    private val muted = Color.rgb(154, 168, 177)
    private val blue = Color.rgb(39, 184, 242)
    private val green = Color.rgb(67, 214, 163)

    private lateinit var status: TextView
    private lateinit var selectedFile: TextView
    private lateinit var format: Spinner
    private lateinit var convert: Button
    private lateinit var progress: ProgressBar
    private var selectedVideo: Uri? = null
    private var pendingOutputName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundColor(this@MainActivity.background)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.audioforge_logo_full)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
        }
        root.addView(logo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)).apply {
            bottomMargin = dp(8)
        })
        root.addView(label("Turn video into sound, beautifully.", 14f, muted, false).apply {
            setPadding(0, dp(4), 0, dp(20))
        })

        val pickPanel = panel().apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { openVideoPicker() }
        }
        pickPanel.addView(label("↓", 34f, blue, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, dp(48)))
        selectedFile = label("Choose a video file", 16f, text, true).apply { gravity = Gravity.CENTER }
        pickPanel.addView(selectedFile)
        pickPanel.addView(label("MP4, MOV, AVI, MKV and more", 12f, muted, false))
        root.addView(pickPanel, LinearLayout.LayoutParams(-1, dp(170)).apply { bottomMargin = dp(16) })

        val optionPanel = panel().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(8), dp(12), dp(8))
        }
        optionPanel.addView(label("OUTPUT FORMAT", 12f, muted, true), LinearLayout.LayoutParams(0, -1, 1f))
        format = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("mp3", "wav", "m4a", "flac"))
            setSelection(0)
        }
        optionPanel.addView(format, LinearLayout.LayoutParams(dp(120), -1))
        root.addView(optionPanel, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(16) })

        convert = Button(this).apply {
            text = "Convert audio"
            textSize = 15f
            setTextColor(Color.rgb(7, 19, 15))
            setBackgroundColor(green)
            setOnClickListener { convertVideo() }
        }
        root.addView(convert, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(surfaceLight)
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(8)))
        status = label("Ready when you are", 12f, muted, false).apply { gravity = Gravity.CENTER }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(label("Created by The DevOps Rite", 12f, muted, false).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(12) })
        return ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = 8f
            setStroke(1, Color.rgb(52, 65, 74))
        }
        isClickable = true
    }

    private fun openVideoPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }, REQUEST_VIDEO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        if (requestCode == REQUEST_VIDEO) {
            selectedVideo = data.data
            selectedFile.text = getFileName(selectedVideo!!) ?: "Video selected"
            status.text = "Video ready to convert"
        }
    }

    private fun convertVideo() {
        val inputUri = selectedVideo ?: run {
            status.text = "Choose a video file first"
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE,
            )
            status.text = "Storage permission is required to save in Downloads"
            return
        }
        convert.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Extracting audio..."
        val extension = format.selectedItem.toString()
        val input = File(cacheDir, "audioforge_input_${System.currentTimeMillis()}.video")
        val output = File(cacheDir, "audioforge_output_${System.currentTimeMillis()}.$extension")
        pendingOutputName = "${getFileName(inputUri)?.substringBeforeLast('.') ?: "audio"}.$extension"
        try {
            val source = contentResolver.openInputStream(inputUri)
                ?: throw IllegalStateException("Could not open the selected video")
            source.use {
                input.outputStream().use { target -> it.copyTo(target) }
            }

        } catch (error: Exception) {
            finishConversion(input, output, false, error.message ?: "Could not read video")
            return
        }
        val codec = when (extension) {
            "mp3" -> arrayOf("-c:a", "libmp3lame", "-q:a", "2")
            "wav" -> arrayOf("-c:a", "pcm_s16le")
            "m4a" -> arrayOf("-c:a", "aac", "-b:a", "192k")
            else -> arrayOf("-c:a", "flac")
        }
        val command = arrayOf(
            "-hide_banner",
            "-loglevel",
            "error",
            "-probesize",
            "100M",
            "-analyzeduration",
            "100M",
            "-y",
            "-i",
            input.path,
            "-vn",
            "-sn",
            "-dn",
            "-map",
            "0:a:0?",
            *codec,
            output.path,
        )
        try {
            val ffmpeg = FFmpeg.getInstance(this)
            ffmpeg.loadBinary(object : LoadBinaryResponseHandler() {
                override fun onStart() {
                    runOnUiThread { status.text = "Preparing audio extractor..." }
                }

                override fun onSuccess() {
                    executeConversion(ffmpeg, command, input, output)
                }

                override fun onFailure() {
                    runOnUiThread { finishConversion(input, output, false, "FFmpeg could not start") }
                }

                override fun onFinish() = Unit
            })
        } catch (error: Exception) {
            finishConversion(input, output, false, error.message ?: "FFmpeg could not start")
        }
    }

    private fun executeConversion(ffmpeg: FFmpeg, command: Array<String>, input: File, output: File) {
        try {
            ffmpeg.execute(command, object : ExecuteBinaryResponseHandler() {
                override fun onStart() {
                    runOnUiThread { status.text = "Extracting audio..." }
                }

                override fun onProgress(message: String?) {
                    runOnUiThread {
                        if (message?.contains("time=", ignoreCase = true) == true) {
                            status.text = "Extracting audio..."
                        }
                    }
                }

                override fun onFailure(message: String?) {
                    runOnUiThread {
                        finishConversion(
                            input,
                            output,
                            false,
                            formatFfmpegError(message),
                        )
                    }
                }

                override fun onSuccess(message: String?) {
                    runOnUiThread { finishConversion(input, output, true, "") }
                }

                private fun formatFfmpegError(message: String?): String {
                    val details = message
                        ?.lineSequence()
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.lastOrNull()
                    return when {
                        details == null -> "FFmpeg could not extract audio from this video"
                        details.contains("Stream map", ignoreCase = true) ||
                            details.contains("matches no streams", ignoreCase = true) ->
                            "No audio stream was found in the selected video"
                        details.contains("Unknown encoder", ignoreCase = true) ->
                            "The selected audio format is not supported by this FFmpeg build"
                        else -> "Audio extraction failed: $details"
                    }
                }

                override fun onFinish() = Unit
            })
        } catch (error: Exception) {
            runOnUiThread {
                finishConversion(input, output, false, error.message ?: "FFmpeg could not start")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            convertVideo()
        }
    }

    private fun finishConversion(input: File, output: File, success: Boolean, error: String) {
        input.delete()
        convert.isEnabled = true
        if (!success) {
            progress.visibility = View.GONE
            output.delete()
            status.text = error
            return
        }
        if (!output.isFile || output.length() == 0L) {
            progress.visibility = View.GONE
            output.delete()
            status.text = "Extraction finished without producing an audio file"
            return
        }
        status.text = "Saving audio to Downloads..."
        Thread {
            saveOutputToDownloads(output, pendingOutputName ?: "audio.mp3")
        }.start()
    }

    private fun saveOutputToDownloads(output: File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType(fileName))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val destination = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: throw IllegalStateException("Could not create a file in Downloads")
                try {
                    contentResolver.openOutputStream(destination).use { target ->
                        requireNotNull(target) { "Could not open the Downloads file" }
                        output.inputStream().use { source -> source.copyTo(target) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(destination, values, null, null)
                } catch (error: Exception) {
                    contentResolver.delete(destination, null, null)
                    throw error
                }
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
                if (!downloads.exists() && !downloads.mkdirs()) {
                    throw IllegalStateException("Could not access the Downloads folder")
                }
                val destination = File(downloads, fileName)
                output.inputStream().use { source ->
                    destination.outputStream().use { target -> source.copyTo(target) }
                }
            }
            output.delete()
            runOnUiThread {
                progress.visibility = View.GONE
                status.text = "Audio saved to Downloads/$fileName"
            }
        } catch (error: Exception) {
            output.delete()
            runOnUiThread {
                progress.visibility = View.GONE
                status.text = error.message ?: "Could not save audio to Downloads"
            }
        }
    }

    private fun mimeType(fileName: String): String = when {
        fileName.endsWith(".mp3", true) -> "audio/mpeg"
        fileName.endsWith(".wav", true) -> "audio/wav"
        fileName.endsWith(".m4a", true) -> "audio/mp4"
        fileName.endsWith(".flac", true) -> "audio/flac"
        else -> "audio/*"
    }

    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    companion object {
        private const val REQUEST_VIDEO = 10
        private const val REQUEST_STORAGE = 12
    }
}
