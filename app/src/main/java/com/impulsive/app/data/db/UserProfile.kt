package com.impulsive.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // singleton row
    val baselineSessionsPerWeek: Int = 0,
    val path: String = "Psychological", // "Psychological" | "Spiritual"
    val identityAnchor: String = "",    // "Mental Clarity" | "Relationship" | "Faith" | "Self-Respect"
    val triggers: String = "",          // comma-separated: "Bored,Stressed,Lonely,Tired,Habit"
    val onboardingComplete: Boolean = false,
    val lastSessionCompleteTimestamp: Long = 0L,
    // comma-separated package names e.g. "com.instagram.android,com.zhiliaoapp.musically"
    val monitoredApps: String = ""
)
