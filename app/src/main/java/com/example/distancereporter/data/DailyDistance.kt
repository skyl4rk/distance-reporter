package com.example.distancereporter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "daily_distances")
data class DailyDistance(
    @PrimaryKey
    val date: LocalDate,
    val distanceMeters: Double
)

@Dao
interface DailyDistanceDao {
    @Query("SELECT * FROM daily_distances WHERE date = :date")
    suspend fun getDistanceForDate(date: LocalDate): DailyDistance?

    @Query("SELECT * FROM daily_distances WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getDistancesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyDistance>>

    @Upsert
    suspend fun upsertDistance(dailyDistance: DailyDistance)
}

class LocalDateConverter {
    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}

@Database(entities = [DailyDistance::class], version = 1, exportSchema = false)
@TypeConverters(LocalDateConverter::class)
abstract class DistanceDatabase : RoomDatabase() {
    abstract fun dailyDistanceDao(): DailyDistanceDao

    companion object {
        @Volatile
        private var INSTANCE: DistanceDatabase? = null

        fun getDatabase(context: android.content.Context): DistanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DistanceDatabase::class.java,
                    "distance_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
