package com.halo.island.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.halo.island.core.*
import com.halo.island.system.HaloNotificationListener
import com.halo.island.system.sendUserPendingIntent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MediaTrack(
    val id: String,
    val packageName: String,
    val title: String,
    val artist: String,
    val source: String,
    val artwork: Bitmap?,
    val accent: Int,
    val duration: Long,
    val position: Long,
    val updated: Long,
    val speed: Float,
    val playing: Boolean,
    val actions: Long,
) {
    fun supports(action: Long) = actions and action != 0L

    fun progress(now: Long = SystemClock.elapsedRealtime()) =
        playbackPosition(position, updated, now, speed, playing, duration)
}

class MediaSessionRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, HaloNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = linkedMapOf<String, MediaController>()
    private val callbacks = mutableMapOf<String, MediaController.Callback>()
    private val _tracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()
    private val _selected = MutableStateFlow<MediaTrack?>(null)
    val selected = _selected.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private var pin: String? = null
    private var listening = false
    private val artworkCache = android.util.LruCache<Int, Pair<Bitmap?, Int>>(8)
    private var refreshJob: Job? = null
    private var generation = 0

    init {
        scope.launch {
            settings.state.map { it.followPlaying }.distinctUntilChanged().collect { choose() }
        }
    }

    private val listener =
        MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    fun connect() {
        if (listening) return refresh()
        try {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            listening = true
            _connected.value = true
            refresh()
        } catch (_: SecurityException) {
            disconnect()
        }
    }

    fun refresh() {
        if (!listening) return
        try {
            attach(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            disconnect()
        }
    }

    fun disconnect() {
        generation++
        refreshJob?.cancel()
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
        listening = false
        _connected.value = false
        controllers.forEach { (id, c) ->
            callbacks[id]?.let { runCatching { c.unregisterCallback(it) } }
        }
        controllers.clear()
        callbacks.clear()
        _tracks.value = emptyList()
        _selected.value = null
        artworkCache.evictAll()
    }

    private fun attach(list: List<MediaController>) {
        val ids = list.map { it.sessionToken.toString() }.toSet()
        controllers.keys
            .toList()
            .filter { it !in ids }
            .forEach { id ->
                callbacks.remove(id)?.let { controllers[id]?.unregisterCallback(it) }
                controllers.remove(id)
            }
        list.forEach { c ->
            val id = c.sessionToken.toString()
            if (id !in controllers) {
                val callback =
                    object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

                        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

                        override fun onSessionDestroyed() {
                            callbacks.remove(id)?.let { runCatching { c.unregisterCallback(it) } }
                            controllers.remove(id)
                            publish()
                        }
                    }
                controllers[id] = c
                callbacks[id] = callback
                c.registerCallback(callback, handler)
            }
        }
        publish()
    }

    private fun publish() {
        val version = ++generation
        refreshJob?.cancel()
        val snapshot = controllers.map { (id, c) -> Triple(id, c, c.metadata to c.playbackState) }
        refreshJob =
            scope.launch {
                val result =
                    withContext(Dispatchers.Default) {
                        snapshot.map { (id, c, pair) ->
                            val (m, p) = pair
                            val original =
                                m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                                    ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                            val key = original?.generationId ?: 0
                            val art =
                                artworkCache.get(key)
                                    ?: run {
                                        val thumb =
                                            original?.let { bitmap ->
                                                val ratio =
                                                    (384f / maxOf(bitmap.width, bitmap.height))
                                                        .coerceAtMost(1f)
                                                runCatching {
                                                        Bitmap.createScaledBitmap(
                                                            bitmap,
                                                            (bitmap.width * ratio)
                                                                .toInt()
                                                                .coerceAtLeast(1),
                                                            (bitmap.height * ratio)
                                                                .toInt()
                                                                .coerceAtLeast(1),
                                                            true,
                                                        )
                                                    }
                                                    .getOrNull()
                                            }
                                        val accent =
                                            thumb?.let(::accent) ?: Color.rgb(183, 217, 209)
                                        (thumb to accent).also { artworkCache.put(key, it) }
                                    }
                            val name =
                                runCatching {
                                        context.packageManager
                                            .getApplicationLabel(
                                                context.packageManager.getApplicationInfo(
                                                    c.packageName,
                                                    0,
                                                )
                                            )
                                            .toString()
                                    }
                                    .getOrDefault(c.packageName)
                            MediaTrack(
                                id,
                                c.packageName,
                                m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Untitled audio",
                                m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                                name,
                                art.first,
                                art.second,
                                m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
                                p?.position ?: 0,
                                p?.lastPositionUpdateTime ?: 0,
                                p?.playbackSpeed ?: 1f,
                                p?.state == PlaybackState.STATE_PLAYING,
                                p?.actions ?: 0,
                            )
                        }
                    }
                if (version == generation) {
                    _tracks.value = result
                    choose()
                }
            }
    }

    private fun accent(bitmap: Bitmap): Int {
        // Bounded sampling off the UI thread; excludes extreme shadows/highlights.
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in 0 until bitmap.height step maxOf(1, bitmap.height / 18)) for (x in
            0 until bitmap.width step maxOf(1, bitmap.width / 18)) {
            val c = runCatching { bitmap.getPixel(x, y) }.getOrDefault(0)
            val luminance = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
            if (luminance in 35..225) {
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                count++
            }
        }
        return if (count == 0) Color.rgb(183, 217, 209)
        else Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun choose() {
        val id =
            selectSession(
                tracks.value.map { MediaCandidate(it.id, it.playing, it.updated) },
                pin,
                settings.state.value.followPlaying,
            )
        _selected.value = tracks.value.firstOrNull { it.id == id }
    }

    fun select(id: String) {
        pin = id
        _selected.value = tracks.value.firstOrNull { it.id == id }
    }

    private fun act(required: Long, action: (MediaController) -> Unit) {
        val t = selected.value ?: return
        if (t.supports(required))
            controllers[t.id]?.let { runCatching { action(it) }.onFailure { refresh() } }
    }

    fun toggle() {
        val t = selected.value ?: return
        if (t.supports(PlaybackState.ACTION_PLAY_PAUSE))
            act(PlaybackState.ACTION_PLAY_PAUSE) {
                if (t.playing) it.transportControls.pause() else it.transportControls.play()
            }
        else if (t.playing) act(PlaybackState.ACTION_PAUSE) { it.transportControls.pause() }
        else act(PlaybackState.ACTION_PLAY) { it.transportControls.play() }
    }

    fun next() = act(PlaybackState.ACTION_SKIP_TO_NEXT) { it.transportControls.skipToNext() }

    fun previous() =
        act(PlaybackState.ACTION_SKIP_TO_PREVIOUS) { it.transportControls.skipToPrevious() }

    fun seek(position: Long) =
        act(PlaybackState.ACTION_SEEK_TO) {
            it.transportControls.seekTo(
                position.coerceIn(0, selected.value?.duration?.coerceAtLeast(0) ?: 0)
            )
        }

    fun openSource() {
        selected.value?.let { t ->
            val c = controllers[t.id]
            val pending = c?.sessionActivity
            val sent = pending?.let { context.sendUserPendingIntent(it) } ?: false
            if (!sent)
                runCatching {
                    context.packageManager
                        .getLaunchIntentForPackage(t.packageName)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let(context::startActivity)
                }
        }
    }
}
