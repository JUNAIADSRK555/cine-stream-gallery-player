package com.cinestream.galleryplayer.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cinestream.galleryplayer.model.M3uChannel
import com.cinestream.galleryplayer.model.M3uPlaylist
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PlaylistStorageManager {
    private const val FILE_NAME = "cine_saved_playlists.json"
    private val gson = Gson()

    suspend fun loadPlaylists(context: Context): List<M3uPlaylist> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return@withContext emptyList()
            val json = file.readText()
            if (json.isBlank()) return@withContext emptyList()
            val type = object : TypeToken<List<M3uPlaylist>>() {}.type
            gson.fromJson<List<M3uPlaylist>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun savePlaylists(context: Context, playlists: List<M3uPlaylist>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val json = gson.toJson(playlists)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Singleton state holder that survives screen recreation and player transitions.
 */
object PlaylistStateHolder {
    var playlists by mutableStateOf<List<M3uPlaylist>>(emptyList())
    var selectedPlaylistId by mutableStateOf<String?>(null)
    var isInitialized by mutableStateOf(false)

    suspend fun initialize(context: Context) {
        if (!isInitialized) {
            val loaded = PlaylistStorageManager.loadPlaylists(context)
            playlists = loaded
            selectedPlaylistId = loaded.firstOrNull()?.id
            isInitialized = true
        }
    }

    suspend fun addPlaylist(context: Context, playlist: M3uPlaylist) {
        playlists = playlists + playlist
        selectedPlaylistId = playlist.id
        PlaylistStorageManager.savePlaylists(context, playlists)
    }

    suspend fun removePlaylist(context: Context, playlistId: String) {
        playlists = playlists.filter { it.id != playlistId }
        if (selectedPlaylistId == playlistId) {
            selectedPlaylistId = playlists.firstOrNull()?.id
        }
        PlaylistStorageManager.savePlaylists(context, playlists)
    }

    suspend fun updatePlaylistChannels(context: Context, playlistId: String, newChannels: List<M3uChannel>) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(channels = newChannels) else it
        }
        PlaylistStorageManager.savePlaylists(context, playlists)
    }
}
