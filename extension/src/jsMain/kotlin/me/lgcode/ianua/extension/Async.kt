package me.lgcode.ianua.extension

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

// Bare stdlib coroutines, no kotlinx-coroutines. Its JS dispatcher crashed inside the MV3
// service worker ("Fatal exception in coroutines machinery"), and nothing here needs
// dispatchers or structured concurrency: every suspension is a chrome.* promise.

/** Starts [block] now and runs it until its first suspension. Failures are logged. */
fun launch(block: suspend () -> Unit) {
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) { result ->
            result.exceptionOrNull()?.let { console.error("Ianua:", it) }
        },
    )
}

suspend fun <T> Promise<T>.await(): T = suspendCoroutine { continuation ->
    then({ continuation.resume(it) }, { continuation.resumeWithException(it) })
}
