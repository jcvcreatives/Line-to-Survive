package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesizes in-memory/cache 16-bit PCM WAV audio files at runtime (requiring zero external
 * assets) and plays:
 * - Looping background music via Android's [MediaPlayer]
 * - Low-latency sound effects for jumping, drawing, landing, button clicks, level complete,
 *   and game over states via Android's [SoundPool].
 */
class SoundEffectManager(
  context: Context? = null,
  initialEnabled: Boolean = true
) {
  var isSoundEnabled: Boolean = initialEnabled
    private set

  var isBackgroundMusicPlaying: Boolean = false
    private set

  // Diagnostic counters for deterministic verification in unit/Robolectric tests
  var jumpTriggerCount: Int = 0
    private set
  var drawTriggerCount: Int = 0
    private set
  var gameOverTriggerCount: Int = 0
    private set
  var levelCompleteTriggerCount: Int = 0
    private set

  private val sampleRate = 22050
  private val audioDir: File = (context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp"))
    .resolve("draw_to_survive_synth_audio")
    .apply { mkdirs() }

  private var soundPool: SoundPool? = null
  private var loadedSoundIds = mutableSetOf<Int>()

  private var jumpSoundId: Int = 0
  private var landSoundId: Int = 0
  private var drawScratchSoundId: Int = 0
  private var lineCompleteSoundId: Int = 0
  private var gameOverSoundId: Int = 0
  private var levelCompleteSoundId: Int = 0
  private var clickSoundId: Int = 0

  private var bgmMediaPlayer: MediaPlayer? = null
  private var bgmFile: File? = null
  private var lastDrawScratchRealtimeMs: Long = 0L

  @Volatile
  private var isInitialized: Boolean = false

  init {
    if (context != null) {
      Thread(
        {
          initializeAudioResources()
        },
        "SoundEffectSynthInit"
      ).apply {
        isDaemon = true
        start()
      }
    } else {
      initializeAudioResources()
    }
  }

  private fun initializeAudioResources() {
    try {
      // 1. Generate procedural WAV files in cache directory (no external assets needed)
      val jumpFile = writeWavFile(
        "sfx_jump.wav",
        listOf(
          ToneStep(startFreq = 260.0, endFreq = 590.0, durationMs = 115, volume = 0.34f, noiseMix = 0.06f)
        )
      )
      val landFile = writeWavFile(
        "sfx_land.wav",
        listOf(
          ToneStep(startFreq = 190.0, endFreq = 92.0, durationMs = 55, volume = 0.26f, noiseMix = 0.22f)
        )
      )
      val drawScratchFile = writeWavFile(
        "sfx_draw_scratch.wav",
        listOf(
          ToneStep(startFreq = 680.0, endFreq = 920.0, durationMs = 38, volume = 0.20f, noiseMix = 0.55f)
        )
      )
      val lineCompleteFile = writeWavFile(
        "sfx_line_complete.wav",
        listOf(
          ToneStep(startFreq = 360.0, endFreq = 540.0, durationMs = 55, volume = 0.30f, noiseMix = 0.15f),
          ToneStep(startFreq = 560.0, endFreq = 760.0, durationMs = 70, volume = 0.28f, noiseMix = 0.08f)
        )
      )
      val gameOverFile = writeWavFile(
        "sfx_game_over.wav",
        listOf(
          ToneStep(startFreq = 420.0, endFreq = 290.0, durationMs = 140, volume = 0.38f, noiseMix = 0.32f),
          ToneStep(startFreq = 280.0, endFreq = 190.0, durationMs = 160, volume = 0.38f, noiseMix = 0.28f),
          ToneStep(startFreq = 185.0, endFreq = 105.0, durationMs = 260, volume = 0.40f, noiseMix = 0.22f)
        )
      )
      val levelCompleteFile = writeWavFile(
        "sfx_level_complete.wav",
        listOf(
          ToneStep(startFreq = 523.25, endFreq = 523.25, durationMs = 90, volume = 0.32f),
          ToneStep(startFreq = 659.25, endFreq = 659.25, durationMs = 90, volume = 0.32f),
          ToneStep(startFreq = 783.99, endFreq = 783.99, durationMs = 100, volume = 0.34f),
          ToneStep(startFreq = 1046.50, endFreq = 1046.50, durationMs = 220, volume = 0.36f)
        )
      )
      val clickFile = writeWavFile(
        "sfx_click.wav",
        listOf(
          ToneStep(startFreq = 520.0, endFreq = 680.0, durationMs = 42, volume = 0.25f)
        )
      )

      bgmFile = writeWavFile("bgm_sketchbook_loop.wav", buildBackgroundMusicSteps())

      // 2. Initialize SoundPool for low-latency triggers
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

      val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(attrs)
        .build()

      pool.setOnLoadCompleteListener { _, sampleId, status ->
        if (status == 0) {
          loadedSoundIds.add(sampleId)
        }
      }

      jumpSoundId = pool.load(jumpFile.absolutePath, 1)
      landSoundId = pool.load(landFile.absolutePath, 1)
      drawScratchSoundId = pool.load(drawScratchFile.absolutePath, 1)
      lineCompleteSoundId = pool.load(lineCompleteFile.absolutePath, 1)
      gameOverSoundId = pool.load(gameOverFile.absolutePath, 1)
      levelCompleteSoundId = pool.load(levelCompleteFile.absolutePath, 1)
      clickSoundId = pool.load(clickFile.absolutePath, 1)
      soundPool = pool

      // 3. Initialize MediaPlayer for looping background music
      prepareMediaPlayer()
      isInitialized = true
      if (isBackgroundMusicPlaying && isSoundEnabled) {
        try {
          bgmMediaPlayer?.start()
        } catch (_: Throwable) {}
      }
    } catch (_: Throwable) {
      // Degrade gracefully on headless JVM environments
    }
  }

  private fun prepareMediaPlayer() {
    val file = bgmFile ?: return
    if (!file.exists()) return
    try {
      bgmMediaPlayer?.release()
      bgmMediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        setDataSource(file.absolutePath)
        isLooping = true
        setVolume(0.22f, 0.22f)
        prepare()
      }
    } catch (_: Throwable) {
      bgmMediaPlayer = null
    }
  }

  fun setSoundEnabledState(enabled: Boolean, shouldPlayBgm: Boolean) {
    isSoundEnabled = enabled
    if (!enabled) {
      pauseBackgroundMusic()
    } else if (shouldPlayBgm) {
      startBackgroundMusic()
    }
  }

  fun startBackgroundMusic() {
    if (!isSoundEnabled) {
      isBackgroundMusicPlaying = false
      return
    }
    isBackgroundMusicPlaying = true
    try {
      val player = bgmMediaPlayer
      if (player != null && !player.isPlaying) {
        player.start()
      }
    } catch (_: Throwable) {
      // Ignore hardware state errors in headless tests
    }
  }

  fun pauseBackgroundMusic() {
    isBackgroundMusicPlaying = false
    try {
      val player = bgmMediaPlayer
      if (player != null && player.isPlaying) {
        player.pause()
      }
    } catch (_: Throwable) {
      // Ignore hardware state errors in headless tests
    }
  }

  fun playJump() {
    if (!isSoundEnabled) return
    jumpTriggerCount++
    playSoundPoolSample(jumpSoundId, volume = 0.85f, rate = 1.0f)
  }

  fun playStepLand() {
    if (!isSoundEnabled) return
    playSoundPoolSample(landSoundId, volume = 0.65f, rate = 1.0f)
  }

  fun playDrawingScratch() {
    if (!isSoundEnabled) return
    val now = try {
      SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
      System.currentTimeMillis()
    }
    // Throttle continuous drag sound to ~85ms so SoundPool streams aren't flooded
    if (now - lastDrawScratchRealtimeMs >= 85L || lastDrawScratchRealtimeMs == 0L) {
      lastDrawScratchRealtimeMs = now
      drawTriggerCount++
      val pitchJitter = 0.94f + Random.nextFloat() * 0.14f
      playSoundPoolSample(drawScratchSoundId, volume = 0.55f, rate = pitchJitter)
    }
  }

  fun playLineCompleted() {
    if (!isSoundEnabled) return
    drawTriggerCount++
    playSoundPoolSample(lineCompleteSoundId, volume = 0.85f, rate = 1.0f)
  }

  fun playLavaGameOver() {
    pauseBackgroundMusic()
    if (!isSoundEnabled) return
    gameOverTriggerCount++
    playSoundPoolSample(gameOverSoundId, volume = 0.95f, rate = 1.0f)
  }

  fun playLevelComplete() {
    pauseBackgroundMusic()
    if (!isSoundEnabled) return
    levelCompleteTriggerCount++
    playSoundPoolSample(levelCompleteSoundId, volume = 0.95f, rate = 1.0f)
  }

  fun playButtonClick() {
    if (!isSoundEnabled) return
    playSoundPoolSample(clickSoundId, volume = 0.75f, rate = 1.0f)
  }

  fun release() {
    isBackgroundMusicPlaying = false
    try {
      bgmMediaPlayer?.stop()
    } catch (_: Throwable) {}
    try {
      bgmMediaPlayer?.release()
    } catch (_: Throwable) {}
    bgmMediaPlayer = null

    try {
      soundPool?.release()
    } catch (_: Throwable) {}
    soundPool = null
  }

  private fun playSoundPoolSample(soundId: Int, volume: Float, rate: Float) {
    if (soundId == 0) return
    try {
      soundPool?.play(soundId, volume, volume, 1, 0, rate)
    } catch (_: Throwable) {
      // Ignore on headless JVM
    }
  }

  private data class ToneStep(
    val startFreq: Double,
    val endFreq: Double,
    val durationMs: Int,
    val volume: Float,
    val noiseMix: Float = 0f
  )

  /**
   * Builds a rhythmic, lighthearted 16-note sketchbook background loop (approx 3.2 seconds)
   * that loops seamlessly in [MediaPlayer].
   */
  private fun buildBackgroundMusicSteps(): List<ToneStep> {
    // A minor / C major pentatonic sketchbook groove
    val melodyFreqs = listOf(
      220.00, 261.63, 329.63, 392.00,
      329.63, 261.63, 293.66, 329.63,
      261.63, 329.63, 392.00, 440.00,
      392.00, 329.63, 293.66, 246.94
    )
    return melodyFreqs.mapIndexed { index, freq ->
      val accent = if (index % 4 == 0) 0.24f else 0.17f
      ToneStep(
        startFreq = freq,
        endFreq = freq,
        durationMs = 200,
        volume = accent,
        noiseMix = 0.02f
      )
    }
  }

  /**
   * Synthesizes a standard 44-byte RIFF/WAVE header + 16-bit mono PCM payload into [fileName].
   */
  private fun writeWavFile(fileName: String, steps: List<ToneStep>): File {
    val outFile = File(audioDir, fileName)
    val totalSamples = steps.sumOf { (sampleRate * it.durationMs) / 1000 }.coerceAtLeast(1)
    val pcmBytes = ByteArray(totalSamples * 2)
    val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

    var phase = 0.0
    for (step in steps) {
      val count = (sampleRate * step.durationMs) / 1000
      for (i in 0 until count) {
        if (!pcmBuffer.hasRemaining()) break
        val progress = i.toDouble() / count.coerceAtLeast(1)
        val freq = step.startFreq + (step.endFreq - step.startFreq) * progress
        phase += 2.0 * PI * freq / sampleRate
        val envelope = exp(-2.2 * progress).toFloat()
        val sineSample = sin(phase).toFloat()
        val harmonic = (0.25 * sin(phase * 2.0)).toFloat()
        val tone = (sineSample + harmonic) * 0.8f
        val noiseSample = if (step.noiseMix > 0f) (Random.nextFloat() * 2f - 1f) else 0f
        val mixed = ((1f - step.noiseMix) * tone + step.noiseMix * noiseSample)
        val sampleShort = (mixed * step.volume * envelope * Short.MAX_VALUE)
          .toInt()
          .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          .toShort()
        pcmBuffer.putShort(sampleShort)
      }
    }

    val dataSize = pcmBytes.size
    val totalChunkSize = 36 + dataSize
    val byteRate = sampleRate * 2 // 16-bit mono = 2 bytes per sample

    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray(Charsets.US_ASCII))
      putInt(totalChunkSize)
      put("WAVE".toByteArray(Charsets.US_ASCII))
      put("fmt ".toByteArray(Charsets.US_ASCII))
      putInt(16) // PCM chunk size
      putShort(1) // AudioFormat = 1 (PCM)
      putShort(1) // NumChannels = 1 (Mono)
      putInt(sampleRate)
      putInt(byteRate)
      putShort(2) // BlockAlign = 2
      putShort(16) // BitsPerSample = 16
      put("data".toByteArray(Charsets.US_ASCII))
      putInt(dataSize)
    }.array()

    FileOutputStream(outFile).use { fos ->
      fos.write(header)
      fos.write(pcmBytes)
    }
    return outFile
  }
}
