package com.resdownload.android.feature.auth

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class OAuthCallbackBus @Inject constructor() {
    private val _events = MutableStateFlow<Uri?>(null)
    val events: StateFlow<Uri?> = _events.asStateFlow()

    fun publish(uri: Uri?) {
        if (uri != null) _events.value = uri
    }

    fun consume(uri: Uri) {
        _events.compareAndSet(uri, null)
    }
}
