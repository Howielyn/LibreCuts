package com.tharunbirla.librecuts

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.tharunbirla.librecuts.customviews.CustomVideoSeeker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Suppress("DEPRECATION")
class VideoEditingActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: StyledPlayerView
    private lateinit var tvDuration: TextView
    private lateinit var frameRecyclerView: RecyclerView
    private lateinit var customVideoSeeker: CustomVideoSeeker
    private var videoUri: Uri? = null
    private var videoFileName: String = ""
    private lateinit var tempInputFile: File
    private lateinit var loadingScreen: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editing)
        loadingScreen = findViewById(R.id.loadingScreen)

        // Initialize UI components and setup the player
        initializeViews()
        setupExoPlayer()
        setupCustomSeeker()
        setupFrameRecyclerView()
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        tvDuration = findViewById(R.id.tvDuration)
        frameRecyclerView = findViewById(R.id.frameRecyclerView)
        customVideoSeeker = findViewById(R.id.customVideoSeeker)

        // Set up button click listeners
        findViewById<ImageButton>(R.id.btnHome).setOnClickListener { onBackPressedDispatcher.onBackPressed()}
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveAction() }
        findViewById<ImageButton>(R.id.btnDel).setOnClickListener { deleteAction() }
        findViewById<ImageButton>(R.id.btnTrim).setOnClickListener { trimAction() }
    }

    private fun deleteAction() {
        // Placeholder for future implementation of delete/undo action
    }

    private fun trimAction() {
        val currentSeekTime = player.currentPosition
        val videoDuration = player.duration

        // Validate the current seek position
        if (currentSeekTime <= 0 || currentSeekTime >= videoDuration) {
            Toast.makeText(this, "Cannot trim at the start or end of the video.", Toast.LENGTH_SHORT).show()
            return
        }

        // Retrieve the video URI from the intent
        val videoUri = intent.getParcelableExtra<Uri>("VIDEO_URI")
        if (videoUri == null) {
            Toast.makeText(this, "Error retrieving video URI", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch video metadata asynchronously
        lifecycleScope.launch {
            try {
                val media = getVideoMetadata(this@VideoEditingActivity, videoUri)
                val realFilePath = media.uri.toString() // Get the actual file path

                // Show dialog to choose trim option
                val options = arrayOf("Keep left portion", "Keep right portion")
                MaterialAlertDialogBuilder(this@VideoEditingActivity)
                    .setTitle("Trim Your Video")
                    .setItems(options) { _: DialogInterface, which: Int ->
                        when (which) {
                            0 -> trimRight(currentSeekTime, realFilePath) // Trim right
                            1 -> trimLeft(currentSeekTime, realFilePath) // Trim left
                        }
                    }
                    .setIcon(R.drawable.ic_split_24)
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Log.e("MetadataError", "Error fetching video metadata: ${e.message}")
                Toast.makeText(this@VideoEditingActivity, "Error fetching video metadata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun trimLeft(newSeekTime: Long, inputPath: String) {
        Log.d("TrimAction", "Input file path: $inputPath")

        val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!outputDir.exists()) {
            outputDir.mkdirs() // Create output directory if it doesn't exist
        }

        val outputPath = File(outputDir, "trimmed_left_${System.currentTimeMillis()}.mp4").absolutePath
        Log.d("TrimAction", "Output file path: $outputPath")
        val trimTimeFormatted = String.format(Locale.getDefault(),"%02d:%02d", newSeekTime / 60000, (newSeekTime / 1000) % 60)
        val command = "-ss $trimTimeFormatted -i \"$inputPath\" -c copy \"$outputPath\""
        Log.d("FFmpegCommand", "FFmpeg command: $command")

        executeFFmpegCommand(command, outputPath)
    }

    private fun trimRight(newSeekTime: Long, inputPath: String) {
        Log.d("TrimAction", "Input file path: $inputPath")

        val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!outputDir.exists()) {
            outputDir.mkdirs() // Create output directory if it doesn't exist
        }

        val outputPath = File(outputDir, "trimmed_right_${System.currentTimeMillis()}.mp4").absolutePath
        Log.d("TrimAction", "Output file path: $outputPath")
        val trimTimeFormatted = String.format(Locale.getDefault(),"%02d:%02d", newSeekTime / 60000, (newSeekTime / 1000) % 60)
        val command = "-ss 00:00 -i \"$inputPath\" -to $trimTimeFormatted -c copy \"$outputPath\""
        Log.d("FFmpegCommand", "FFmpeg command: $command")

        executeFFmpegCommand(command, outputPath)
    }

    private fun executeFFmpegCommand(command: String, outputPath: String) {
        FFmpegKit.executeAsync(command) { session ->
            runOnUiThread {
                if (ReturnCode.isSuccess(session.returnCode)) {
                    Log.d("TrimSuccess", "Video trimmed successfully!")
                    Toast.makeText(this, "Video trimmed successfully!", Toast.LENGTH_SHORT).show()
                    videoUri = Uri.parse(outputPath) // Update video URI to the trimmed video
                    refreshPlayer() // Refresh player with new video
                    refreshUI() // Update UI components
                } else {
                    Log.e("TrimError", "Error trimming video: ${session.returnCode}")
                    Toast.makeText(this, "Error trimming video: ${session.returnCode}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshUI() {
        // Update UI elements based on the player's current state
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    customVideoSeeker.setVideoDuration(player.duration)
                    updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                    extractVideoFrames() // Refresh frame list for the trimmed video
                }
            }
        })

        // Call to extract frames for display
        extractVideoFrames()
    }

    private fun refreshPlayer() {
        player.release() // Release the current player instance

        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this // Bind player to the player view
            setMediaItem(MediaItem.fromUri(videoUri!!)) // Set the new media item
            prepare() // Prepare the player
            playWhenReady = false // Start playback automatically
            seekTo(0) // Seek to the start of the video
        }

        // Update the custom seeker to reflect the new video's duration
        customVideoSeeker.setVideoDuration(player.duration)
        updateDurationDisplay(0, player.duration.toInt()) // Reset duration display
    }


    private fun saveAction() {
        // Placeholder for future implementation of save functionality
    }

    private fun setupExoPlayer() {
        videoUri = intent.getParcelableExtra("VIDEO_URI") // Retrieve the video URI from intent
        if (videoUri != null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player // Bind player to the view

            val mediaItem = MediaItem.fromUri(videoUri!!)
            player.setMediaItem(mediaItem) // Set media item for the player

            // Show loading screen while preparing the video
            loadingScreen.visibility = View.VISIBLE

            player.prepare() // Prepare the player for playback

            // Get the actual file path from the URI
            val videoFilePath = getFilePathFromUri(videoUri!!)
            Log.d("Path","File path: $videoFilePath")
            if (videoFilePath != null) {
                tempInputFile = File(videoFilePath) // Set temporary input file to the real file path
                videoFileName = File(videoFilePath).name // Store the file name

                // Fetch video metadata asynchronously
                lifecycleScope.launch {
                    try {
                        val media = getVideoMetadata(this@VideoEditingActivity, videoUri!!)
                        Log.d("MetadataSuccess", "Media: $media")

                        // Call to extract frames for display after loading
                        extractVideoFrames() // Extract frames after metadata is fetched

                    } catch (e: Exception) {
                        Log.e("MetadataError", "Error fetching video metadata: ${e.message}")
                        Toast.makeText(this@VideoEditingActivity, "Error fetching video metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
//                        // Hide loading screen after 5 seconds
//                        Handler(Looper.getMainLooper()).postDelayed({
//                            loadingScreen.visibility = View.GONE
//                        }, 500) // 5000 milliseconds = 5 seconds
                    }
                }
            } else {
                Log.e("VideoLoadError", "Error loading video")
                Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show()
                return
            }

            // Add listener for player events
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        customVideoSeeker.setVideoDuration(player.duration) // Set duration on seeker
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        val currentPosition = player.currentPosition.toInt()
                        updateDurationDisplay(currentPosition, player.duration.toInt()) // Update displayed duration
                    }
                }
            })
        } else {
            Log.e("VideoLoadError", "Error loading video")
            Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show()
        }
    }


    private fun getFilePathFromUri(uri: Uri): String? {
        var filePath: String? = null

        when (uri.scheme) {
            "content" -> {
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        // Attempt to get the file path from cursor
                        val dataIndex = it.getColumnIndex("_data")
                        if (dataIndex != -1) {
                            filePath = it.getString(dataIndex) // Fetch file path
                        }
                    }
                }
            }
            "file" -> {
                filePath = uri.path // Directly get path from URI
            }
            else -> {
                Log.e("PathError", "Unsupported URI scheme: ${uri.scheme}")
            }
        }

        Log.d("PathInfo", "File path: $filePath")
        return filePath
    }

    private fun setupCustomSeeker() {
        // Configure the custom video seeker for seeking playback
        customVideoSeeker.onSeekListener = { seekPosition ->
            val newSeekTime = (player.duration * seekPosition).toLong()

            // Ensure new seek time is within valid bounds
            if (newSeekTime >= 0 && newSeekTime <= player.duration) {
                player.seekTo(newSeekTime) // Seek to new position
                updateDurationDisplay(newSeekTime.toInt(), player.duration.toInt()) // Update duration display
            } else {
                Log.d("SeekError", "Seek position out of bounds.")
            }
        }
    }

    private fun setupFrameRecyclerView() {
        frameRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        frameRecyclerView.adapter = FrameAdapter(emptyList()) // Initialize frame adapter with video name
    }

    private fun extractVideoFrames() {
        // This function no longer needs to show/hide loading screen
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempInputFile.absolutePath)

            // Switch to the main thread to access player properties
            val duration = withContext(Dispatchers.Main) { player.duration }
            val frameInterval = duration / 10 // Extract fewer frames

            val frameBitmaps = mutableListOf<Bitmap>()
            val frameCount = 10 // Adjust to change how many frames you extract

            for (i in 0 until frameCount) {
                val frameTime = (i * frameInterval) // Time in microseconds
                val bitmap = retriever.getFrameAtTime(frameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                bitmap?.let {
                    val processedBitmap = Bitmap.createScaledBitmap(it, 200, 150, false) // Example resizing
                    frameBitmaps.add(processedBitmap)
                }
            }

            retriever.release()

            // Switch back to the main thread to update the RecyclerView
            withContext(Dispatchers.Main) {
                frameRecyclerView.adapter = FrameAdapter(frameBitmaps)
                // Hide loading screen after 5 seconds
                loadingScreen.visibility = View.GONE
            }
        }
    }





    @SuppressLint("SetTextI18n")
    private fun updateDurationDisplay(current: Int, total: Int) {
        Log.d("VideoEditingActivity", "Current: $current, Total: $total")

        // Format and display the current and total duration
        val currentFormatted = String.format(Locale.getDefault(),"%02d:%02d", current / 60000, (current / 1000) % 60)
        val totalFormatted = String.format(Locale.getDefault(),"%02d:%02d", total / 60000, (total / 1000) % 60)

        Log.d("DurationDisplay", "Current: $currentFormatted / Total: $totalFormatted")

        tvDuration.text = "$currentFormatted / $totalFormatted" // Update duration text view
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release() // Release player resources on destruction
    }

    /**
     * Fetch video metadata based on URI.
     *
     * @param context The context to access content resolver
     * @param uri The URI of the video
     * @return Media object containing metadata
     */
    private suspend fun getVideoMetadata(context: Context, uri: Uri): Media {
        return withContext(Dispatchers.IO) {
            val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            // Define projection for querying video metadata
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATA // Fetch the real file path
            )

            val selection = "${MediaStore.Video.Media._ID} = ?"
            val selectionArgs = arrayOf(uri.lastPathSegment)

            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val displayNameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeTypeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                if (cursor.moveToFirst()) {
                    // Retrieve metadata from cursor
                    val fileName = cursor.getString(displayNameColumnIndex)
                    val size = cursor.getLong(sizeColumnIndex)
                    val mimeType = cursor.getString(mimeTypeColumnIndex)
                    val realFilePath = cursor.getString(dataColumnIndex)

                    // Log metadata for debugging
                    Log.d(TAG, "File Name: $fileName")
                    Log.d(TAG, "File Size: $size bytes")
                    Log.d(TAG, "MIME Type: $mimeType")
                    Log.d("MetadataInfo", "File Name: $fileName, Size: $size bytes, MIME Type: $mimeType, Real File Path: $realFilePath")


                    // Return Media object containing the metadata
                    return@use Media(Uri.parse(realFilePath), fileName, size, mimeType)
                } else {
                    Log.e("MetadataError", "cursor.moveToFirst() returned false")
                    throw Error("cursor.moveToFirst() method returned false")
                }
            } ?: run {
                Log.e("MetadataError", "Unexpected null from contentResolver query")
                throw Error("Unexpected null returned by contentResolver query")
            }
        }
    }

    data class Media(
        val uri: Uri,
        val name: String,
        val size: Long,
        val mimeType: String
    )

    companion object {
        private const val TAG = "VideoMetadata"
    }
}