package com.finora.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.Flow

val Context.appearance by preferencesDataStore("appearance")

@Entity(tableName = "bank_review", primaryKeys = ["uid", "id"])
data class Candidate(
    val uid: String,
    val id: String,
    val bank: String,
    val amount: Long,
    val credit: Boolean,
    val time: Long,
    val masked: String,
    val reference: String,
    val confidence: Double,
)

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(record: Candidate)

    @Query("SELECT * FROM bank_review WHERE uid=:uid ORDER BY time DESC")
    fun observe(uid: String): Flow<List<Candidate>>

    @Query("DELETE FROM bank_review WHERE uid=:uid AND id=:id")
    suspend fun remove(uid: String, id: String)

    @Query("DELETE FROM bank_review WHERE uid=:uid") suspend fun clear(uid: String)
}

@Database(entities = [Candidate::class], version = 1, exportSchema = false)
abstract class LocalLedger : RoomDatabase() {
    abstract fun reviews(): ReviewDao

    companion object {
        @Volatile private var instance: LocalLedger? = null

        fun get(c: Context): LocalLedger =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                c.applicationContext,
                                LocalLedger::class.java,
                                "finora-local",
                            )
                            .build()
                            .also { instance = it }
                }
    }
}
