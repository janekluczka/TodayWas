package pl.luczka.todaywas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 0,
    val onboardingCompleted: Boolean,
    val hasSyncedLocalData: Boolean = false,
)
