package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feedback_responses",
    indices = [
        Index(
            value = ["promptDateEpochDay"],
            unique = true,
        ),
        Index(value = ["expiresAtMillis"]),
        Index(value = ["answeredAtMillis"]),
    ],
)
data class FeedbackResponseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val promptDateEpochDay: Long,
    val questionIndex: Int,
    val questionText: String,
    val positiveAnswerText: String,
    val honestAnswerText: String,
    val selectedAnswerIndex: Int? = null,
    val createdAtMillis: Long,
    val answeredAtMillis: Long? = null,
    val expiresAtMillis: Long,
    val updatedAtMillis: Long,
)
