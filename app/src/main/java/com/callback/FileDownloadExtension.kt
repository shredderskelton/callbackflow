package com.callback

import android.util.Log
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The storage reference is usually the default one setup with Firebase and google-play-services.json
 * The filename is the reference to the file you wish to download.
 */
@ExperimentalCoroutinesApi
fun StorageReference.download(filename: String): Flow<DownloadResult> =
// We are using callbackFlow because we are wrapping the Firebase SDKs "callback" mechanism,
// Another word for a callback is a "Listener"!
// the callback/listener mechanism is as old as Android itself and Google SDKs use them
    // ubiquitously, thus the convenience callbackFlow builder.
    callbackFlow {
        var task: FileDownloadTask? = null
        val remoteDirectory = this@download

        // Create an empty random file to download into
        val tempFile = createTemporaryFile()

        // Check that the remote file actually exists
        if (!remoteDirectory.exists(filename)) {
            // Here we are emitting a failure into the Flow, then close the flow to signal that it's done.
            send(DownloadResult.Complete.Failed(Exception("File doesn't exist in this remote location")))
            close()
        } else {
            task = remoteDirectory.child(filename)
                .getFile(tempFile)
            // Here is the "callback" - the reason we are using "callbackFlow"
            // The idea is that the callbackFlow creates a Flow, and you can emit things into the
            // flow when the underlying object 'calls us back'
            task
                .addOnSuccessListener {
                    if (!isClosedForSend)
                        offer(DownloadResult.Complete.Success(tempFile))
                    close()
                }
                .addOnFailureListener {
                    if (!isClosedForSend)
                        offer(DownloadResult.Complete.Failed(it))
                    close(it)
                }
                .addOnPausedListener {
                    if (!isClosedForSend)
                        offer(DownloadResult.Paused(it))
                }
                .addOnProgressListener { task ->
                    val progressResult = DownloadResult.InProgress(task)
                    if (!isClosedForSend) {
                        (1..2).forEach {
                            if (!offer(progressResult)) Log.e("PROG", "Busy")
                        }
                    }
                }
                .addOnCanceledListener {
                    offer(DownloadResult.Complete.Cancelled)
                    close(CancellationException("Download was cancelled"))
                }
        }

        awaitClose {
            Log.e("CLOSE", "callbackFlow was closed")
        }
    }
        // This is the default: meaning we can emit 64 values before offer will start to return false, and emissions will be dropped
        // .buffer(Channel.BUFFERED)
        // .buffer(64)
        // This is an interesting variant, this creates a FIFO buffer
        .buffer(10, BufferOverflow.DROP_OLDEST)
// Unlimited can cause Memory to run out, esp if you are streaming large chunks of data
// .buffer(Channel.UNLIMITED)

// This is a decent alternative in some cases. This will help in this case, because it is important that the final emission from this Flow
// Namely, the Download.Completed.XXX value, is not dropped
//.buffer(Channel.CONFLATED)
//.conflate()
//.buffer(1, BufferOverflow.DROP_OLDEST)

private suspend fun createTemporaryFile(): File {
    return withContext(Dispatchers.IO) { File.createTempFile("test", ".temp") }
}

private suspend fun StorageReference.exists(filename: String): Boolean {
    return try {
        child(filename).downloadUrl.await()
        true
    } catch (ex: StorageException) {
        false
    }
}