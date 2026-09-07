package com.shohan.khatiyan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Single-row user profile collected during onboarding (Phase 7). */
@Serializable
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Long = 1,
    val name: String = "",
    val currencySymbol: String = "৳",
    val createdAtIso: String = "",
)

/** Per-obligation notification dedupe state (Phase 20) — prevents duplicates. */
@Serializable
@Entity(tableName = "notification_state")
data class NotificationStateEntity(
    /** e.g. "loan:12:2026-09-08" */
    @PrimaryKey val key: String,
    val lastNotifiedIso: String,
)
