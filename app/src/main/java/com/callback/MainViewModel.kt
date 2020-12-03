package com.callback

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class MainViewModel : ViewModel() {

    private val state = MutableStateFlow<DownloadResult?>(null)

    val text: Flow<String> = state.map {
        when (it) {
            null -> "Download"
            is DownloadResult.InProgress -> "Downloading: ${it.task.progress}%"
            is DownloadResult.Paused -> "Paused: ${it.task.progress}%"
            is DownloadResult.Complete.Success -> "Done"
            is DownloadResult.Complete.Failed -> "Failed: ${it.error.localizedMessage}"
            DownloadResult.Complete.Cancelled -> "Cancelled"
        }
    }
    val progress: Flow<Int> =
        state.filterIsInstance<DownloadResult.InProgress>().map { it.task.progress }

    val buttonStartVisible: Flow<Boolean> =
        state.map { it == null || it is DownloadResult.Complete }

    val buttonPauseVisible: Flow<Boolean> =
        state.isInstanceOf<DownloadResult.InProgress>()

    val buttonResumeVisible: Flow<Boolean> =
        state.isInstanceOf<DownloadResult.Paused>()

    val buttonCancelVisible: Flow<Boolean> =
        combine(buttonPauseVisible, buttonResumeVisible) { array -> array.any { it } }

    private var cancellableTask: FileDownloadTask.TaskSnapshot? = null

    fun download() {
        viewModelScope.launch {
            FirebaseStorage.getInstance().reference.download("tanz.mov")
                .collectIndexed { _, value: DownloadResult ->
                    cancellableTask = when (value) {
                        is DownloadResult.InProgress -> value.task
                        is DownloadResult.Paused -> value.task
                        else -> null
                    }
                    // simulate long processing time
                    (1..10).forEach {
                        delay(100)
                        println("$it")
                    }

                    state.value = value
                }
        }
    }

    fun cancel() {
        cancellableTask?.task?.cancel()
    }

    fun pause() {
        cancellableTask?.task?.pause()
    }

    fun resume() {
        cancellableTask?.task?.resume()
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified R> Flow<*>.isInstanceOf(): Flow<Boolean> = map { it is R }

fun <T> LifecycleOwner.bind(property: Flow<T>, block: (T) -> Unit) {
    lifecycleScope.launch {
        property.collectLatest { block(it) }
    }
}

val FileDownloadTask.TaskSnapshot.progress
    get() = ((bytesTransferred.toFloat() / totalByteCount.absoluteValue.toFloat()) * 100).toInt()