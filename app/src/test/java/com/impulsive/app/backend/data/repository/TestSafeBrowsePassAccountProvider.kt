package com.impulsive.app.backend.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestSafeBrowsePassAccountProvider(
    initialUid: String? = "test-safe-browse-user",
) : SafeBrowsePassAccountProvider {
    private val uidState = MutableStateFlow(initialUid)

    override val authenticatedUid: Flow<String?> = uidState

    override fun currentAuthenticatedUid(): String? = uidState.value

    fun setUid(uid: String?) {
        uidState.value = uid
    }
}
