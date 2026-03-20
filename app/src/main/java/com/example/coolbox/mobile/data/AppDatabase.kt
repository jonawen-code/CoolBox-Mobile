package com.example.coolbox.mobile.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import java.util.concurrent.Executors

@Database(entities = [FoodEntity::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coolbox_database"
                )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                // 使用 TRUNCATE 模式避免 WAL 文件造成的指纹冲突
                .setJournalMode(JournalMode.TRUNCATE)
                // 查询日志，方便排查 schema 问题
                .setQueryCallback({ sqlQuery, _ ->
                    Log.d("RoomQuery", sqlQuery)
                }, Executors.newSingleThreadExecutor())
                .build()
                INSTANCE = instance
                instance
            }
        }

        // 供 CloudSyncManager 调用：彻底关闭并清除单例
        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }

        // 终极手段：删除数据库文件并重置单例，调用后需重新 getDatabase()
        fun deleteAndReset(context: Context) {
            destroyInstance()
            val dbPath = context.getDatabasePath("coolbox_database")
            dbPath.delete()
            File(dbPath.absolutePath + "-wal").delete()
            File(dbPath.absolutePath + "-shm").delete()
            Log.w("AppDatabase", "数据库文件已彻底删除，将在下次访问时重建")
        }
    }
}