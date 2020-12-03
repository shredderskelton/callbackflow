package com.callback

import com.google.firebase.storage.FileDownloadTask
import java.io.File

/**
 * You'll need the following libraries:
 *
 * To use Firebase Storage:
 * implementation "com.google.firebase:firebase-storage-ktx"
 *
 * Gives us the 'lifecycleScope' extension function used in the Activity
 * 'viewModelScope' also comes in this package
 * implementation "androidx.lifecycle:lifecycle-runtime-ktx"
 *
 * Gives us the callbackFlow builder
 * implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core"
 *
 * This gives us the handy 'await' function when checking if the file exists or not
 * implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services"
 */
sealed class DownloadResult {

    data class InProgress(val task: FileDownloadTask.TaskSnapshot) : DownloadResult()
    data class Paused(val task: FileDownloadTask.TaskSnapshot) : DownloadResult()

    sealed class Complete : DownloadResult() {
        data class Success(val file: File) : Complete()
        data class Failed(val error: Throwable) : Complete()
        object Cancelled : Complete()
    }
}