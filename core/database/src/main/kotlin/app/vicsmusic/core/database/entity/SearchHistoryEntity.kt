package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [
        Index("searchedAt")
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long
)
