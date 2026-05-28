package com.samnick.voiceboard.overlay

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Single-path audio capture used by both transcription engines:
 *   - records 16 kHz mono PCM_16BIT (whisper.cpp's preferred input)
 *   - writes a complete WAV file at stop (cheap header, Groq accepts WAV upload)
 *   - exposes the PCM frames as a FloatArray for the local engine
 */
class AudioCapture(private val outFile: File) {

  private val sampleRate = 16_000
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

  private var record: AudioRecord? = null
  private var thread: Thread? = null
  @Volatile private var running = false
  private val pcm = ByteBuffer.allocate(sampleRate * 2 * 90) // up to 90 s
      .order(ByteOrder.LITTLE_ENDIAN)

  @SuppressLint("MissingPermission")
  fun start() {
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    val buf = (minBuf * 2).coerceAtLeast(4096)
    val rec = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        channelConfig,
        audioFormat,
        buf,
    )
    if (rec.state != AudioRecord.STATE_INITIALIZED) {
      throw IllegalStateException("AudioRecord init failed")
    }
    record = rec
    rec.startRecording()
    running = true
    thread = thread(name = "voiceboard-recorder", isDaemon = true) {
      val chunk = ByteArray(buf)
      while (running) {
        val n = rec.read(chunk, 0, chunk.size)
        if (n > 0) {
          synchronized(pcm) {
            val space = pcm.remaining()
            if (space <= 0) { running = false; return@thread }
            pcm.put(chunk, 0, minOf(n, space))
          }
        }
      }
    }
  }

  /** Returns the PCM byte count actually captured. */
  fun stop(): Int {
    running = false
    try { thread?.join(500) } catch (_: Throwable) {}
    val rec = record
    record = null
    try { rec?.stop() } catch (_: Throwable) {}
    try { rec?.release() } catch (_: Throwable) {}

    val bytes = synchronized(pcm) {
      pcm.flip()
      val arr = ByteArray(pcm.remaining())
      pcm.get(arr)
      arr
    }
    writeWav(outFile, bytes, sampleRate, channels = 1, bitsPerSample = 16)
    return bytes.size
  }

  /** Reads the just-written WAV back as float32 mono PCM, normalised to [-1, 1]. */
  fun readAsFloats(): FloatArray {
    val raw = outFile.readBytes()
    if (raw.size <= 44) return FloatArray(0)
    val pcmBytes = raw.copyOfRange(44, raw.size)
    val out = FloatArray(pcmBytes.size / 2)
    var i = 0; var j = 0
    while (i + 1 < pcmBytes.size) {
      val sample = (pcmBytes[i].toInt() and 0xFF) or (pcmBytes[i + 1].toInt() shl 8)
      val signed = sample.toShort().toInt()
      out[j++] = signed / 32768f
      i += 2
    }
    return out
  }

  private fun writeWav(
      file: File,
      pcm: ByteArray,
      sampleRate: Int,
      channels: Int,
      bitsPerSample: Int,
  ) {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcm.size
    val totalSize = 36 + dataSize

    FileOutputStream(file).use { fos ->
      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      header.put("RIFF".toByteArray())
      header.putInt(totalSize)
      header.put("WAVE".toByteArray())
      header.put("fmt ".toByteArray())
      header.putInt(16) // subchunk1 size
      header.putShort(1) // PCM
      header.putShort(channels.toShort())
      header.putInt(sampleRate)
      header.putInt(byteRate)
      header.putShort(blockAlign.toShort())
      header.putShort(bitsPerSample.toShort())
      header.put("data".toByteArray())
      header.putInt(dataSize)
      fos.write(header.array())
      fos.write(pcm)
    }
  }
}
