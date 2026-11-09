package br.com.voz99.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_places")
data class FavoritePlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_places ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FavoritePlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: FavoritePlace)

    @Delete
    suspend fun delete(place: FavoritePlace)
}

@Database(entities = [FavoritePlace::class], version = 1, exportSchema = false)
abstract class FavoritesDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile private var instance: FavoritesDatabase? = null
        fun get(context: Context): FavoritesDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FavoritesDatabase::class.java,
                "favorite_places.db"
            ).build().also { instance = it }
        }
    }
}
