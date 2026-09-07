package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shohan.khatiyan.data.local.entity.NotificationStateEntity
import com.shohan.khatiyan.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    abstract fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    abstract suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertProfile(profile: UserProfileEntity)
}

@Dao
abstract class NotificationStateDao {

    @Query("SELECT lastNotifiedIso FROM notification_state WHERE key = :key")
    abstract suspend fun lastNotifiedIso(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun mark(state: NotificationStateEntity)

    @Query("DELETE FROM notification_state")
    abstract suspend fun clear()
}
