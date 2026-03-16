package com.example.coolbox.mobile.data

import com.google.gson.annotations.SerializedName

data class FoodEntity(
    val id: String = "",
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
    
    // 【核心补丁】：强行把 JSON 里的 "remark" 塞给这里的 note
    // 如果你确定 NAS 吐出来的是别的词（比如 "memo" 或 "description"），直接改引号里的词就行！
    @SerializedName("remark") 
    val note: String = "",
    
    val lastModifiedMs: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)