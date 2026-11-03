package com.morningflow.app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.morningflow.app.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

interface AudioController {
    fun startBackgroundMusic(volume: Float)

    fun stopBackgroundMusic()

    /**
     * Plays [segment], returning only once the line has finished so the caller can
     * time the pause that follows it. A segment with no recording is spoken instead,
     * which is how the leaving reminder's runtime-built lines are read out.
     */
    suspend fun playSegment(segment: NarrationSegment, volume: Float)

    /** Speaks [text] and returns once it has finished. */
    suspend fun speak(text: String, volume: Float)

    fun stopGuidance()

    fun playCue(enabled: Boolean)

    fun release()
}

/**
 * Plays guidance from bundled recordings and falls back to the device's
 * text-to-speech engine, which also carries the leaving reminder because its wording
 * is only known once the day's weather and calendar have been read.
 *
 * It shares the phone with other music apps: while a line is spoken, music from apps
 * like Spotify is lowered and comes back up in the quiet between lines, and the
 * routine's own background track stays off if other music is already playing.
 */
class AndroidAudioController(context: Context) : AudioController, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Asks other apps to lower, not pause, their audio while a line is spoken. */
    private val speechFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()
    private var holdingSpeechFocus = false
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, CUE_VOLUME)

    @Volatile
    private var ttsReady = false
    private var clipPlayer: MediaPlayer? = null
    private var musicPlayer: MediaPlayer? = null
    private var pendingSpeech: ((Unit) -> Unit)? = null

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        tts.language = Locale.UK
        tts.setAudioAttributes(speechAttributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finishSpeech()

            @Deprecated("Superseded by onError(String, Int)", ReplaceWith("finishSpeech()"))
            override fun onError(utteranceId: String?) = finishSpeech()

            override fun onError(utteranceId: String?, errorCode: Int) = finishSpeech()

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finishSpeech()
        })
    }

    /**
     * One looped track for the whole session. A second call only adjusts the volume,
     * so a settings change does not restart the music mid-morning.
     *
     * The track is mastered louder than the voice recordings, so it plays at
     * [MUSIC_GAIN] of the chosen volume to sit well under the guidance, and it fades in
     * rather than starting at full level the moment the session (or a resume) begins.
     */
    override fun startBackgroundMusic(volume: Float) {
        val target = volume * MUSIC_GAIN
        musicPlayer?.let { playing ->
            cancelMusicFade()
            playing.setVolume(target, target)
            if (!playing.isPlaying) playing.start()
            return
        }
        // The user is already listening to something of their own; don't play over it.
        if (audioManager.isMusicActive) return
        val player = MediaPlayer.create(appContext, R.raw.background_music) ?: return
        player.isLooping = true
        player.setVolume(0f, 0f)
        musicPlayer = player
        player.start()
        fadeMusicIn(player, target)
    }

    override fun stopBackgroundMusic() {
        cancelMusicFade()
        musicPlayer?.release()
        musicPlayer = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var musicFade: Runnable? = null

    private fun fadeMusicIn(player: MediaPlayer, target: Float) {
        var step = 0
        val fade = object : Runnable {
            override fun run() {
                if (musicPlayer !== player) return
                step++
                // Squared so the rise is gentle at first, as loudness is heard logarithmically.
                val progress = (step.toFloat() / MUSIC_FADE_STEPS).coerceAtMost(1f)
                val level = target * progress * progress
                player.setVolume(level, level)
                if (step < MUSIC_FADE_STEPS) mainHandler.postDelayed(this, MUSIC_FADE_MILLIS / MUSIC_FADE_STEPS)
                else musicFade = null
            }
        }
        musicFade = fade
        mainHandler.post(fade)
    }

    private fun cancelMusicFade() {
        musicFade?.let(mainHandler::removeCallbacks)
        musicFade = null
    }

    private val releaseSpeechFocus = Runnable { abandonSpeechFocus() }

    private fun lowerOtherAudio() {
        mainHandler.removeCallbacks(releaseSpeechFocus)
        if (!holdingSpeechFocus) {
            holdingSpeechFocus = audioManager.requestAudioFocus(speechFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /**
     * Lets other audio back up shortly after a line ends. Lines within a breath of each
     * other keep it lowered, so the music does not bob up and down between them.
     */
    private fun restoreOtherAudioSoon() {
        mainHandler.removeCallbacks(releaseSpeechFocus)
        mainHandler.postDelayed(releaseSpeechFocus, SPEECH_FOCUS_RELEASE_MILLIS)
    }

    private fun abandonSpeechFocus() {
        mainHandler.removeCallbacks(releaseSpeechFocus)
        if (holdingSpeechFocus) audioManager.abandonAudioFocusRequest(speechFocus)
        holdingSpeechFocus = false
    }

    override suspend fun playSegment(segment: NarrationSegment, volume: Float) {
        if (segment.clipRes == NarrationSegment.NO_CLIP) {
            speak(segment.text, volume)
            return
        }
        lowerOtherAudio()
        try {
            playClip(segment, volume)
        } finally {
            restoreOtherAudioSoon()
        }
    }

    private suspend fun playClip(segment: NarrationSegment, volume: Float) {
        suspendCancellableCoroutine { continuation ->
            val player = MediaPlayer.create(appContext, segment.clipRes, speechAttributes, audioManager.generateAudioSessionId())
            if (player == null) {
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            clipPlayer = player
            player.setVolume(volume, volume)
            player.setOnCompletionListener {
                releaseClipPlayer()
                if (continuation.isActive) continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { releaseClipPlayer() }
            player.start()
        }
    }

    override suspend fun speak(text: String, volume: Float) {
        if (!ttsReady || text.isBlank()) return
        lowerOtherAudio()
        try {
            speakAndWait(text, volume)
        } finally {
            restoreOtherAudioSoon()
        }
    }

    private suspend fun speakAndWait(text: String, volume: Float) {
        suspendCancellableCoroutine { continuation ->
            pendingSpeech = { if (continuation.isActive) continuation.resume(Unit) }
            continuation.invokeOnCancellation {
                pendingSpeech = null
                tts.stop()
            }
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
            if (queued != TextToSpeech.SUCCESS) finishSpeech()
        }
    }

    override fun stopGuidance() {
        tts.stop()
        finishSpeech()
        releaseClipPlayer()
        abandonSpeechFocus()
    }

    override fun playCue(enabled: Boolean) {
        if (enabled) toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, CUE_DURATION_MILLIS)
    }

    override fun release() {
        stopGuidance()
        stopBackgroundMusic()
        tts.shutdown()
        toneGenerator.release()
    }

    private fun releaseClipPlayer() {
        clipPlayer?.release()
        clipPlayer = null
    }

    private fun finishSpeech() {
        val resume = pendingSpeech ?: return
        pendingSpeech = null
        resume(Unit)
    }

    private companion object {
        const val UTTERANCE_ID = "morningflow-guidance"
        const val CUE_VOLUME = 45
        const val CUE_DURATION_MILLIS = 140

        /** About 5 dB under the voice at the same volume setting. */
        const val MUSIC_GAIN = 0.53f

        const val SPEECH_FOCUS_RELEASE_MILLIS = 1_200L
        const val MUSIC_FADE_MILLIS = 6_000L
        const val MUSIC_FADE_STEPS = 60
    }
}
