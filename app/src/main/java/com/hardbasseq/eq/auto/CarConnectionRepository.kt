package com.hardbasseq.eq.auto

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.car.app.connection.CarConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the phone is projecting to a car (Android Auto) or runs inside one (Automotive OS). */
interface CarConnectionRepository {
    val carConnected: StateFlow<Boolean>
}

@Singleton
class AndroidCarConnectionRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CarConnectionRepository {
        private val _carConnected = MutableStateFlow(false)
        override val carConnected: StateFlow<Boolean> = _carConnected.asStateFlow()

        init {
            // CarConnection.getType() is a LiveData, which can only be observed on the main thread.
            Handler(Looper.getMainLooper()).post {
                CarConnection(context).type.observeForever { type ->
                    val projecting = type == CarConnection.CONNECTION_TYPE_PROJECTION
                    val native = type == CarConnection.CONNECTION_TYPE_NATIVE
                    _carConnected.value = projecting || native
                }
            }
        }
    }
