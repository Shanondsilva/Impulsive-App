package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.FutureSelfMessage
import com.impulsive.app.backend.data.local.preferences.FutureSelfMessageDataSource
import kotlinx.coroutines.flow.Flow

class FutureSelfMessageRepository(context: Context) {
    private val dataSource = FutureSelfMessageDataSource(context.applicationContext)

    val message: Flow<FutureSelfMessage?> = dataSource.message

    suspend fun saveVoice(filePath: String, createdAtEpochMillis: Long) {
        dataSource.saveVoice(filePath, createdAtEpochMillis)
    }

    suspend fun saveText(text: String, createdAtEpochMillis: Long) {
        dataSource.saveText(text, createdAtEpochMillis)
    }

    suspend fun delete() {
        dataSource.delete()
    }
}
