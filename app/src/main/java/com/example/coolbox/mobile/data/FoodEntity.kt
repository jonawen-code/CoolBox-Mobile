package com.example.coolbox.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_items")
data class FoodEntity(
    @PrimaryKey val id: String = "",
    val icon: String = "ic_food_default",
    val name: String = "未命名食品",
    val fridgeName: String = "未知位置",
    val inputDateMs: Long = System.currentTimeMillis(),
    val expiryDateMs: Long = System.currentTimeMillis(),
    val quantity: Double = 0.0,
    val weightPerPortion: Double = 0.0,
    val portions: Int = 1,
    val category: String = "未分类",
    val unit: String = "个",
    val remark: String = "",
    val lastModifiedMs: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
