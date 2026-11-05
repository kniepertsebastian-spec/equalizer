package com.hardbasseq.eq.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How long [AudioSessionForegroundService] keeps listening with no audio session to work on
 * before it closes itself (open_questions.md Q21: a service that never ends costs too much
 * battery). Listening for a player's session broadcast needs the running service, so after
 * it closes a player only gets the equalizer again once HardBass EQ is opened.
 */
val SESSION_IDLE_TIMEOUT: Duration = 10.minutes

/**
 * Emits one [Unit] once the equalizer has had nothing to work on for a whole [timeout], then
 * completes. "Nothing to work on" means no usable audio session ([hasSession] false) while the
 * app is not on screen ([appVisible] false). A session opening, or the app being opened,
 * restarts the wait - someone looking at the equalizer is never cut off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun idleShutdown(
    hasSession: Flow<Boolean>,
    appVisible: Flow<Boolean>,
    timeout: Duration,
): Flow<Unit> =
    combine(hasSession, appVisible) { session, visible -> !session && !visible }
        .distinctUntilChanged()
        .transformLatest { idle ->
            if (idle) {
                delay(timeout)
                emit(Unit)
            }
        }.take(1)
