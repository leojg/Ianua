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

/**
 * COMPILER PITFALL (Kotlin 2.4 / JS): never index a `dynamic` result straight off a suspend
 * call. `promise.await()[key]` compiles without a suspension point, so it reads `null` and
 * the promise later resumes a finished coroutine ("This continuation is already complete").
 * Always assign first: `val items = promise.await(); items[key]`.
 */
suspend fun <T> Promise<T>.await(): T = suspendCoroutine { continuation ->
    then({ continuation.resume(it) }, { continuation.resumeWithException(it) })
}
