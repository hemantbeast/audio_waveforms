package com.simform.audio_waveforms

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.flutter.plugin.common.MethodChannel

class AudioPlayer(
        context: Context,
        channel: MethodChannel,
        playerKey: String
) {
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var methodChannel = channel
    private var appContext = context
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var isPlayerPrepared: Boolean = false
    private var finishMode = FinishMode.Stop
    private var key = playerKey
    private var updateFrequency: Long = 200

    fun preparePlayer(
            result: MethodChannel.Result,
            path: String?,
            volume: Float?,
            frequency: Long?,
    ) {
        if (path != null) {
            frequency?.let {
                updateFrequency = it
            }
            val uri = Uri.parse(path)
            val mediaItem = MediaItem.fromUri(uri)
            player = ExoPlayer.Builder(appContext).build()
            player?.addMediaItem(mediaItem)
            player?.prepare()
            playerListener = object : Player.Listener {

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    super.onPlayerErrorChanged(error)
                    result.error(Constants.LOG_TAG, error?.message, "Unable to load media source.")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!isPlayerPrepared) {
                        if (playbackState == Player.STATE_READY) {
                            player?.volume = volume ?: 1F
                            isPlayerPrepared = true
                            result.success(true)
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        val args: MutableMap<String, Any?> = HashMap()
                        when (finishMode) {
                            FinishMode.Stop -> {
                                player?.stop()
                                player?.release()
                                player = null
                                stopListening()
                                args[Constants.finishType] = 2
                            }

                            FinishMode.Loop -> {
                                player?.seekTo(0)
                                player?.play()
                                args[Constants.finishType] = 0
                            }

                            FinishMode.Pause -> {
                                player?.seekTo(0)
                                player?.playWhenReady = false
                                stopListening()
                                args[Constants.finishType] = 1
                            }
                        }
                        args[Constants.playerKey] = key
                        methodChannel.invokeMethod(
                            Constants.onDidFinishPlayingAudio,
                            args
                        )
                    }
                }
            }
            player?.addListener(playerListener!!)
        } else {
            result.error(Constants.LOG_TAG, "path to audio file or unique key can't be null", "")
        }
    }

    fun seekToPosition(result: MethodChannel.Result, progress: Long?) {
        if (progress != null) {
            player?.seekTo(progress)
            sendCurrentDuration()
            result.success(true)
        } else {
            result.success(false)
        }
    }

    fun start(result: MethodChannel.Result) {
        try {
            player?.playWhenReady = true
            player?.play()
            result.success(true)
            startListening(result)
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Can not start the player", e.toString())
        }
    }

    fun getDuration(result: MethodChannel.Result, durationType: DurationType) {
        try {
            if (durationType == DurationType.Current) {
                val duration = player?.currentPosition
                result.success(duration)
            } else {
                val duration = player?.duration
                result.success(duration)
            }
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Can not get duration", e.toString())
        }
    }

    fun stop(result: MethodChannel.Result) {
        stopListening()
        if (playerListener != null) {
            player?.removeListener(playerListener!!)
        }
        isPlayerPrepared = false
        player?.stop()
        result.success(true)
    }


    fun pause(result: MethodChannel.Result) {
        try {
            stopListening()
            player?.pause()
            result.success(true)
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Failed to pause the player", e.toString())
        }

    }

    fun release(result: MethodChannel.Result) {
        try {
            player?.release()
            result.success(true)
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Failed to release player resource", e.toString())
        }

    }

    fun setVolume(volume: Float?, result: MethodChannel.Result) {
        try {
            if (volume != null) {
                player?.volume = volume
                result.success(true)
            } else {
                result.success(false)
            }
        } catch (e: Exception) {
            result.success(false)
        }
    }

    fun setRate(rate: Float?, result: MethodChannel.Result) {
        try {
            if (rate != null) {
                player?.setPlaybackSpeed(rate)
                result.success(true)
            } else {
                result.success(false)
            }
        } catch (e: Exception) {
            result.success(false)
        }
    }

    fun setFinishMode(result: MethodChannel.Result, releaseModeType: Int?) {
        try {
            releaseModeType?.let {
                when (releaseModeType) {
                    0 -> {
                        this.finishMode = FinishMode.Loop
                    }

                    1 -> {
                        this.finishMode = FinishMode.Pause
                    }

                    2 -> {
                        this.finishMode = FinishMode.Stop
                    }

                    else -> {
                        throw Exception("Invalid Finish mode")
                    }
                }
            }

        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Can not set the release mode", e.toString())
        }
    }

    private fun startListening(result: MethodChannel.Result) {
        runnable = object : Runnable {
            override fun run() {
                sendCurrentDuration()
                handler.postDelayed(this, updateFrequency)
            }
        }
        handler.post(runnable!!)

    }

    private fun stopListening() {
        runnable?.let { handler.removeCallbacks(it) }
        sendCurrentDuration()
    }

    private fun sendCurrentDuration() {
        val currentPosition = player?.currentPosition ?: 0
        val args: MutableMap<String, Any?> = HashMap()
        args[Constants.current] = currentPosition
        args[Constants.playerKey] = key
        methodChannel.invokeMethod(Constants.onCurrentDuration, args)
    }


}
