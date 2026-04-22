package top.kagg886.pmf.backend.database.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pmf.backend.database.converters.IllustConverter

@Dao
interface IllustArchiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IllustArchive)

    @Query("SELECT * FROM IllustArchive WHERE illustId = :illustId")
    suspend fun find(illustId: Int): IllustArchive?
}

enum class IllustArchiveMediaType {
    IMAGE,
    UGOIRA,
}

@Entity
@TypeConverters(IllustConverter::class)
data class IllustArchive(
    @PrimaryKey(autoGenerate = false)
    val illustId: Int,
    val illust: Illust,
    val mediaType: IllustArchiveMediaType,
    val mediaFiles: String,
    val archiveTime: Long = Clock.System.now().toEpochMilliseconds(),
)

val IllustArchive.fileNames: List<String>
    get() = Json.decodeFromString(mediaFiles)
