package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.security.PhotoVerification
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-tile trust badge. UNSIGNED/UNKNOWN draw nothing — only real signals are surfaced. */
enum class TileStatus { VERIFIED, TAMPERED, UNSIGNED, UNKNOWN }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val photoDao = AppDatabase.get(application).photoDao()

    /** Null until the first database emission, so the screen can tell "loading" from "empty". */
    val photos: StateFlow<List<PhotoEntity>?> = photoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tileStatus = MutableStateFlow<Map<Long, TileStatus>>(emptyMap())
    val tileStatus: StateFlow<Map<Long, TileStatus>> = _tileStatus.asStateFlow()

    /** A system "allow deleting these photos" prompt the screen must launch. */
    private val _deleteConsent = MutableStateFlow<IntentSender?>(null)
    val deleteConsent: StateFlow<IntentSender?> = _deleteConsent.asStateFlow()
    private var idsAwaitingConsent: List<Long> = emptyList()

    init {
        // One verification pass at a time: a new list cancels the old pass,
        // and results are cached so each photo is only read once per session.
        viewModelScope.launch {
            photoDao.observeAll().collectLatest { list -> verifyMissing(list) }
        }
    }

    private suspend fun verifyMissing(list: List<PhotoEntity>) = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val gone = mutableListOf<Long>()
        for (photo in list) {
            if (_tileStatus.value.containsKey(photo.id)) continue
            val uri = Uri.parse(photo.filePath)
            if (!exists(uri)) {
                // Deleted from the system gallery: drop the stale index row.
                gone += photo.id
                continue
            }
            val status = when (PhotoVerification.verifyUri(context, uri)) {
                is VerificationOutcome.Untampered -> TileStatus.VERIFIED
                is VerificationOutcome.Edited -> TileStatus.TAMPERED
                is VerificationOutcome.NoProof -> TileStatus.UNSIGNED
                is VerificationOutcome.Unreadable -> TileStatus.UNKNOWN
            }
            _tileStatus.update { it + (photo.id to status) }
        }
        if (gone.isNotEmpty()) photoDao.deleteByIds(gone)
    }

    /**
     * False only when the file is definitely gone. Any other error (no storage
     * permission on Android 8/9, a busy provider) keeps the row: deleting a
     * photo's index entry, with its hash and signature, on a transient error
     * would be unrecoverable.
     */
    private fun exists(uri: Uri): Boolean = try {
        getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: java.io.FileNotFoundException) {
        false
    } catch (e: Exception) {
        true
    }

    /** Deletes the photos from the device gallery and the index. May ask for consent first. */
    fun delete(photos: List<PhotoEntity>) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            val byUri = photos.associateBy { it.filePath }
            val outcome = withContext(Dispatchers.IO) {
                MediaStoreImageSaver.delete(getApplication(), photos.map { Uri.parse(it.filePath) })
            }
            // Only rows whose file is really gone; the rest wait for consent or stay.
            val deletedIds = outcome.deleted.mapNotNull { byUri[it.toString()]?.id }
            if (deletedIds.isNotEmpty()) photoDao.deleteByIds(deletedIds)
            _tileStatus.update { it - deletedIds.toSet() }
            if (outcome.consent != null) {
                idsAwaitingConsent = outcome.awaitingConsent.mapNotNull { byUri[it.toString()]?.id }
                _deleteConsent.value = outcome.consent
            }
        }
    }

    /** Called with the outcome of the system consent prompt. */
    fun onDeleteConsentResult(granted: Boolean) {
        _deleteConsent.value = null
        val ids = idsAwaitingConsent
        idsAwaitingConsent = emptyList()
        if (granted && ids.isNotEmpty()) viewModelScope.launch { photoDao.deleteByIds(ids) }
    }
}
