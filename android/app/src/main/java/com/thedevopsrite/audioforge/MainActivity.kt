package com.thedevopsrite.audioforge

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import com.writingminds.ffmpeg.ExecuteBinaryResponseHandler
import com.writingminds.ffmpeg.FFmpeg
import com.writingminds.ffmpeg.LoadBinaryResponseHandler
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
    private var pendingOutput: File? = null
    private var pendingOutputName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(44, 36, 44, 24)
            setBackgroundColor(background)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.audioforge_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        root.addView(logo, LinearLayout.LayoutParams(180, 150).apply { bottomMargin = 8 })

        val title = LinearLayout(this).apply { gravity = Gravity.CENTER }
        title.addView(label("Audio", 30f, text, true))
        title.addView(label("Forge", 30f, blue, true))
        root.addView(title)
        root.addView(label("Turn video into sound, beautifully.", 14f, muted, false).apply {
            setPadding(0, 4, 0, 28)
        })

        val pickPanel = panel().apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { openVideoPicker() }
        }
        pickPanel.addView(label("↓", 34f, blue, true))
        selectedFile = label("Choose a video file", 16f, text, true).apply { gravity = Gravity.CENTER }
        pickPanel.addView(selectedFile)
        pickPanel.addView(label("MP4, MOV, AVI, MKV and more", 12f, muted, false))
        root.addView(pickPanel, LinearLayout.LayoutParams(-1, 190).apply { bottomMargin = 22 })

        val optionPanel = panel().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 8, 12, 8)
        }
        optionPanel.addView(label("OUTPUT FORMAT", 12f, muted, true), LinearLayout.LayoutParams(0, -1, 1f))
        format = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("mp3", "wav", "m4a", "flac"))
            setSelection(0)
        }
        optionPanel.addView(format, LinearLayout.LayoutParams(120, -1))
        root.addView(optionPanel, LinearLayout.LayoutParams(-1, 62).apply { bottomMargin = 22 })

        convert = Button(this).apply {
            text = "Convert audio"
            textSize = 15f
            setTextColor(Color.rgb(7, 19, 15))
            setBackgroundColor(green)
            setOnClickListener { convertVideo() }
        }
        root.addView(convert, LinearLayout.LayoutParams(-1, 58).apply { bottomMargin = 12 })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(surfaceLight)
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 8))
        status = label("Ready when you are", 12f, muted, false).apply { gravity = Gravity.CENTER }
        root.addView(status, LinearLayout.LayoutParams(-1, 42))
        root.addView(label("Created by The DevOps Rite", 12f, muted, false).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 40).apply { topMargin = 12 })
        return root
    }

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
        } else if (requestCode == REQUEST_SAVE) {
            saveOutput(data.data!!)
        }
    }

    private fun convertVideo() {
        val inputUri = selectedVideo ?: run {
            status.text = "Choose a video file first"
            return
        }
        convert.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Extracting audio..."
        val extension = format.selectedItem.toString()
        val input = File(cacheDir, "audioforge_input_${System.currentTimeMillis()}.video")
        val output = File(cacheDir, "audioforge_output_${System.currentTimeMillis()}.$extension")
        pendingOutput = output
        pendingOutputName = "${getFileName(inputUri)?.substringBeforeLast('.') ?: "audio"}.$extension"
        try {
            contentResolver.openInputStream(inputUri).use { source ->
                input.outputStream().use { target -> source?.copyTo(target) }
            }
        } catch (error: Exception) {
            finishConversion(input, output, false, error.message ?: "Could not read video")
            return
        }
        val codec = when (extension) {
            "mp3" -> "-c:a libmp3lame -q:a 2"
            "wav" -> "-c:a pcm_s16le"
            "m4a" -> "-c:a aac -b:a 192k"
            else -> "-c:a flac"
        }
        val command = arrayOf("-y", "-i", input.path, "-vn", "-map", "0:a:0", *codec.split(" ").toTypedArray(), output.path)
        try {
            val ffmpeg = FFmpeg.getInstance(this)
            ffmpeg.loadBinary(object : LoadBinaryResponseHandler() {
                override fun onStart() = Unit

                override fun onSuccess() = Unit

                override fun onFailure() {
                    runOnUiThread { finishConversion(input, output, false, "FFmpeg could not start") }
                }

                override fun onFinish() {
                    try {
                        ffmpeg.execute(command, object : ExecuteBinaryResponseHandler() {
                            override fun onStart() = Unit

                            override fun onProgress(message: String?) = Unit

                            override fun onFailure(message: String?) {
                                runOnUiThread { finishConversion(input, output, false, message ?: "Video does not contain a readable audio track") }
                            }

                            override fun onSuccess(message: String?) {
                                runOnUiThread { finishConversion(input, output, true, "") }
                            }

                            override fun onFinish() = Unit
                        })
                    } catch (error: Exception) {
                        runOnUiThread { finishConversion(input, output, false, error.message ?: "FFmpeg could not start") }
                    }
                }
            })
        } catch (error: Exception) {
            finishConversion(input, output, false, error.message ?: "FFmpeg could not start")
        }
    }

    private fun finishConversion(input: File, output: File, success: Boolean, error: String) {
        input.delete()
        convert.isEnabled = true
        progress.visibility = View.GONE
        if (!success) {
            output.delete()
            status.text = error
            return
        }
        status.text = "Choose where to save your audio"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_TITLE, pendingOutputName)
        }, REQUEST_SAVE)
    }

    private fun saveOutput(destination: Uri) {
        val output = pendingOutput ?: return
        try {
            contentResolver.openOutputStream(destination).use { target ->
                output.inputStream().use { source -> source.copyTo(target!!) }
            }
            output.delete()
            status.text = "Audio saved successfully"
        } catch (error: Exception) {
            status.text = error.message ?: "Could not save audio"
        }
    }

    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    companion object {
        private const val REQUEST_VIDEO = 10
        private const val REQUEST_SAVE = 11
    }
}
