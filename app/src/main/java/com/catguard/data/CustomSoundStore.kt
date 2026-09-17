package com.catguard.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.catguard.deterrent.DeterrentSound
import com.catguard.deterrent.SoundPoolAudioController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Result of trying to import a sound, in words the UI can show directly. */
sealed interface ImportResult {
    data class Success(val sound: DeterrentSound.Custom) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/**
 * Sounds the user imported from their own storage.
 *
 * The picked file is **copied** into the app's private directory rather than
 * referenced by URI. A content URI permission can be revoked, the source file can
 * be deleted or live on a removed SD card, and a deterrent that stops working
 * silently at 3am is worse than no deterrent. A local copy always plays.
 *
 * The index is a small text file next to the audio; there is no database for the
 * same reasons as [EventRepository].
 */
class CustomSoundStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = SoundPoolAudioController.customSoundsDir(appContext)
    private val indexFile = File(dir, INDEX_NAME)
    private val mutex = Mutex()

    private val _sounds = MutableStateFlow<List<DeterrentSound.Custom>>(emptyList())
    val sounds: StateFlow<List<DeterrentSound.Custom>> = _sounds.asStateFlow()

    suspend fun load() {
        val parsed = withContext(Dispatchers.IO) {
            if (!indexFile.exists()) return@withContext emptyList()
            runCatching { indexFile.readLines().mapNotNull(::parseLine) }
                .onFailure { Log.e(TAG, "Sound index unreadable; starting fresh", it) }
                .getOrDefault(emptyList())
        }
        // Drop entries whose audio file has gone missing.
        val present = parsed.filter { File(dir, it.fileName).exists() }
        mutex.withLock {
            _sounds.value = present
            if (present.size != parsed.size) persist(present)
        }
    }

    /**
     * Copies [uri] into private storage and adds it to the library.
     *
     * Everything that can go wrong here is something the user can act on, so the
     * failure messages are written for them rather than for a log.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver

        val displayName = queryDisplayName(uri) ?: "Custom sound"
        val extension = displayName.substringAfterLast('.', "").lowercase()
            .takeIf { it in SUPPORTED_EXTENSIONS }
            ?: guessExtension(uri)
            ?: return@withContext ImportResult.Failure(
                "That file type is not supported. Use an MP3, WAV, OGG, M4A or AAC file.",
            )

        val fileName = "${UUID.randomUUID()}.$extension"
        val target = File(dir, fileName)

        try {
            resolver.openInputStream(uri).use { input ->
                if (input == null) {
                    return@withContext ImportResult.Failure("That file could not be opened.")
                }
                target.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (copied > MAX_BYTES) {
                        target.delete()
                        return@withContext ImportResult.Failure(
                            "That file is larger than 10 MB. Deterrent sounds are held in " +
                                "memory, so please use a short clip.",
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Import failed", t)
            runCatching { target.delete() }
            return@withContext ImportResult.Failure("That file could not be copied: ${t.message}")
        }

        val durationMs = probeDuration(target)
        if (durationMs <= 0L) {
            target.delete()
            return@withContext ImportResult.Failure(
                "Android could not decode that file. Try a standard MP3 or a 16-bit PCM WAV.",
            )
        }
        if (durationMs > MAX_DURATION_MS) {
            target.delete()
            return@withContext ImportResult.Failure(
                "That clip is ${durationMs / 1000} seconds long. Please use one under " +
                    "${MAX_DURATION_MS / 1000} seconds.",
            )
        }

        val sound = DeterrentSound.Custom(
            id = "custom_${fileName.substringBefore('.')}",
            displayName = displayName.substringBeforeLast('.').take(40).ifBlank { "Custom sound" },
            durationMs = durationMs,
            fileName = fileName,
        )

        mutex.withLock {
            val updated = _sounds.value + sound
            _sounds.value = updated
            persist(updated)
        }
        ImportResult.Success(sound)
    }

    suspend fun remove(id: String) {
        mutex.withLock {
            val sound = _sounds.value.firstOrNull { it.id == id } ?: return@withLock
            val updated = _sounds.value - sound
            _sounds.value = updated
            withContext(Dispatchers.IO) {
                runCatching { File(dir, sound.fileName).delete() }
                    .onFailure { Log.w(TAG, "Could not delete ${sound.fileName}", it) }
                persist(updated)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull()

    private fun guessExtension(uri: Uri): String? {
        val type = runCatching { appContext.contentResolver.getType(uri) }.getOrNull() ?: return null
        return when {
            type.contains("mpeg") || type.contains("mp3") -> "mp3"
            type.contains("wav") -> "wav"
            type.contains("ogg") || type.contains("vorbis") -> "ogg"
            type.contains("mp4") || type.contains("m4a") || type.contains("aac") -> "m4a"
            type.startsWith("audio/") -> "mp3"
            else -> null
        }
    }

    /**
     * Doubles as a decodability check: if Android cannot read the duration it
     * will not be able to play the file either.
     *
     * MediaMetadataRetriever only became AutoCloseable in API 29, so it is
     * released by hand for the API 24 minimum.
     */
    private fun probeDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read duration of ${file.name}", t)
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun persist(list: List<DeterrentSound.Custom>) {
        runCatching {
            indexFile.writeText(
                list.joinToString("\n") { "${it.id}|${it.fileName}|${it.durationMs}|${it.displayName}" },
            )
        }.onFailure { Log.e(TAG, "Could not persist the sound index", it) }
    }

    private fun parseLine(line: String): DeterrentSound.Custom? {
        val parts = line.trim().split('|', limit = 4)
        if (parts.size != 4) return null
        val duration = parts[2].toLongOrNull() ?: return null
        return DeterrentSound.Custom(
            id = parts[0],
            fileName = parts[1],
            durationMs = duration,
            displayName = parts[3],
        )
    }

    private companion object {
        const val TAG = "CatGuardSounds"
        const val INDEX_NAME = "sounds.index"
        const val MAX_BYTES = 10L * 1024 * 1024
        const val MAX_DURATION_MS = 30_000L
        val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac")
    }
}
