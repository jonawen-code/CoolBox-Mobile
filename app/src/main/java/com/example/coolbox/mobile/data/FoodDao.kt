// Build: 2.0.0
package com.example.coolbox.mobile.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_items WHERE isDeleted = 0 ORDER BY expiryDateMs ASC")
    fun getAllItems(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food_items")
    fun getAllItemsSync(): List<FoodEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: FoodEntity)

    @Query("UPDATE food_items SET isDeleted = 1, lastModifiedMs = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long)

    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' AND isDeleted = 0")
    fun searchFood(query: String): List<FoodEntity>

    @Query("UPDATE food_items SET icon = :defaultIcon, lastModifiedMs = :timestamp WHERE icon IS NULL OR icon = ''")
    suspend fun migrateEmptyIcons(defaultIcon: String, timestamp: Long)

    @Delete
    suspend fun hardDelete(item: FoodEntity)
}
