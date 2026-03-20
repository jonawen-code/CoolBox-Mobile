package com.example.coolbox.mobile.data

import androidx.room.*

@Dao
interface FoodDao {
@Query("SELECT * FROM food_items WHERE isDeleted = 0")
suspend fun getAllVisibleItems(): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: FoodEntity)

    @Update
    suspend fun updateItem(item: FoodEntity)

    
}