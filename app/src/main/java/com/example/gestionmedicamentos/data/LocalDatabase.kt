package com.example.gestionmedicamentos.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import org.json.JSONObject

@Entity(tableName = "local_records", primaryKeys = ["owner", "id"])
data class RecordCacheRow(val owner: String, val id: String, val payload: String)

@Entity(tableName = "local_completions", primaryKeys = ["owner", "id"])
data class CompletionCacheRow(val owner: String, val id: String, val payload: String)

@Entity(tableName = "local_profiles")
data class ProfileCacheRow(@PrimaryKey val owner: String, val payload: String)

@Dao
abstract class LocalDao {
    @Query("SELECT * FROM local_records WHERE owner = :owner")
    abstract fun records(owner: String): List<RecordCacheRow>

    @Query("SELECT * FROM local_completions WHERE owner = :owner")
    abstract fun completions(owner: String): List<CompletionCacheRow>

    @Query("SELECT * FROM local_profiles WHERE owner = :owner LIMIT 1")
    abstract fun profile(owner: String): ProfileCacheRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putRecords(rows: List<RecordCacheRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putCompletions(rows: List<CompletionCacheRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putProfile(row: ProfileCacheRow)

    @Query("DELETE FROM local_records WHERE owner = :owner")
    abstract fun clearRecords(owner: String)

    @Query("DELETE FROM local_completions WHERE owner = :owner")
    abstract fun clearCompletions(owner: String)

    @Transaction
    open fun replaceRecords(owner: String, rows: List<RecordCacheRow>) {
        clearRecords(owner)
        putRecords(rows)
    }

    @Transaction
    open fun replaceCompletions(owner: String, rows: List<CompletionCacheRow>) {
        clearCompletions(owner)
        putCompletions(rows)
    }

    @Transaction
    open fun putCompletion(owner: String, completion: CompletionCacheRow, record: RecordCacheRow?) {
        if (record != null) putRecords(listOf(record))
        putCompletions(listOf(completion))
    }
}

@Database(entities = [RecordCacheRow::class, CompletionCacheRow::class, ProfileCacheRow::class], version = 1, exportSchema = true)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao

    companion object {
        @Volatile private var instance: LocalDatabase? = null
        fun get(context: Context): LocalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, "medivida-local.db").build()
                .also { instance = it }
        }
    }
}

internal fun RecordCacheRow.decode(): HealthRecord {
    val j = JSONObject(payload)
    return HealthRecord(id, Kind.valueOf(j.getString("kind")), j.getString("title"), j.optString("detail"),
        j.getString("times"), j.getString("start"), j.optString("end"), j.optString("place"),
        j.getString("level"), j.getInt("reminderMinutes"), j.getBoolean("dayBefore"), j.optBoolean("archived"),
        if (j.has("stockRemaining") && !j.isNull("stockRemaining")) j.optInt("stockRemaining") else null,
        j.optInt("unitsPerDose", 1), j.optInt("stockAlertAt", 5),
        if (j.has("stockTotal") && !j.isNull("stockTotal")) j.optInt("stockTotal")
        else if (j.has("stockRemaining") && !j.isNull("stockRemaining")) j.optInt("stockRemaining") else null,
        j.optString("foodItems"))
}

internal fun CompletionCacheRow.decode(): Completion {
    val j = JSONObject(payload)
    return Completion(j.getString("recordId"), j.getString("occurrence"), Status.valueOf(j.getString("status")), j.getLong("at"))
}

internal fun ProfileCacheRow.decode(): Profile {
    val j = JSONObject(payload)
    return Profile(j.getString("name"), j.optString("age"), j.optString("weight"), j.optString("blood"),
        j.optString("allergies"), j.optString("contact"), j.optString("phone"), j.optBoolean("notifications", true))
}
