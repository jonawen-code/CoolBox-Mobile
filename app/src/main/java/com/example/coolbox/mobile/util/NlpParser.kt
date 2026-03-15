package com.example.coolbox.mobile.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ParsedResult(
    var name: String = "",
    var quantity: Double = 1.0,
    var unit: String = "个",
    var location: String = "大冰箱",
    var expiryMs: Long = 0L,
    var remark: String = "",
    var portions: Int = 1,
    var isQuery: Boolean = false,
    var category: String = "未分类"
)

object NlpParser {

    private val numberMap = mapOf(
        "一" to 1.0, "两" to 2.0, "二" to 2.0, "三" to 3.0, "四" to 4.0, 
        "五" to 5.0, "六" to 6.0, "七" to 7.0, "八" to 8.0, "九" to 9.0, "十" to 10.0,
        "半" to 0.5
    )

    fun parse(text: String, baseTimeMs: Long = System.currentTimeMillis()): ParsedResult {
        var rawText = text.trim()
        val result = ParsedResult()
        result.expiryMs = baseTimeMs + (30L * 24 * 60 * 60 * 1000) // Default 30 days
        
        // 1. Strategic Stripping: Identify and remove segments to isolate the Food Name
        var workingText = rawText

        // A. Extract Date/Expiry
        val dateRegex = Regex("(\\d{4})[-年](\\d{1,2})[-月](\\d{1,2})")
        dateRegex.find(workingText)?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[3].padStart(2, '0')}"
                result.expiryMs = sdf.parse(dateStr)?.time ?: result.expiryMs
                workingText = workingText.replace(it.value, " [DATE] ")
            } catch (e: Exception) {}
        } ?: run {
            val relativeRegex = Regex("保质期([0-9]+|[一两二三四五六七八九十]+)(个月|周|天|月)")
            relativeRegex.find(workingText)?.let {
                val numStr = it.groupValues[1]
                val num = numStr.toLongOrNull() ?: numberMap[numStr]?.toLong() ?: 0L
                val type = it.groupValues[2]
                val offset = when(type) {
                    "个月", "月" -> num * 30L * 24 * 60 * 60 * 1000
                    "周" -> num * 7L * 24 * 60 * 60 * 1000
                    "天" -> num * 24 * 60 * 60 * 1000
                    else -> 0L
                }
                if (offset > 0) result.expiryMs = System.currentTimeMillis() + offset
                workingText = workingText.replace(it.value, " [EXPIRY] ")
            }
        }

        // B. Extract Location (High Priority Stripping)
        val locationKeywords = listOf(
            "冰柜 第一层", "冰柜 第二层", "冰柜 第三层", "冰柜 第1层", "冰柜 第2层", "冰柜 第3层",
            "冰柜第一层", "冰柜第二层", "冰柜第三层", "冰柜第1层", "冰柜第2层", "冰柜第3层",
            "冰柜上层", "冰柜下层", "大冰箱 冷藏室", "大冰箱 冷冻室", "大冰箱 第一层", "大冰箱 第二层",
            "大冰箱冷藏室", "大冰箱冷冻室", "大冰箱第一层", "大冰箱第二层",
            "第一层", "第二层", "第三层", "第四层", "第1层", "第2层", "第3层", "第4层",
            "冷藏室", "冷冻室", "保鲜层", "冰柜", "大冰箱", "正面", "侧门", "侧壁"
        )
        for (loc in locationKeywords) {
            if (workingText.contains(loc)) {
                result.location = loc
                workingText = workingText.replace(loc, " [LOC] ")
                break // Take the most specific match
            }
        }

        // C. Extract Portions (Matches "分成了6盒", "切成3块", "分了5份")
        val portionRegex = Regex("(?:分成了|分了|分成|切成|共)([0-9]+|[一两二三四五六七八九十]+)(?:份|盒|块|罐|个|瓶|支|包|袋)")
        portionRegex.find(workingText)?.let {
            val numStr = it.groupValues[1]
            result.portions = numStr.toIntOrNull() ?: numberMap[numStr]?.toInt() ?: 1
            workingText = workingText.replace(it.value, " [PORTION] ")
        }

        // D. Extract Quantity & Unit (Matches "两斤", "1.5kg", "3瓶")
        val units = listOf("kg", "公斤", "斤", "包", "袋", "个", "瓶", "支", "升", "l", "ml", "毫升", "盒", "块", "罐")
        unitLoop@for (u in units) {
            val qtyRegex = Regex("([0-9]+(?:\\.[0-9])?|[一两二三四五六七八九十半]+)\\s*$u")
            val match = qtyRegex.find(workingText)
            if (match != null) {
                val numStr = match.groupValues[1]
                result.quantity = numStr.toDoubleOrNull() ?: numberMap[numStr] ?: 1.0
                result.unit = u
                workingText = workingText.replace(match.value, " [QTY] ")
                break@unitLoop
            }
        }

        // 2. Extract Remark and Name from the remaining "骨架" (Skeleton)
        // Heuristic: If "是" exists, the text following it is the remark.
        var skeleton = workingText
        
        // Remove standard filler words and masks
        skeleton = skeleton.replace(Regex("\\[(DATE|EXPIRY|LOC|PORTION|QTY)\\]"), " ")
        skeleton = skeleton.replace(Regex("(买|买了|放在|放进了|放入|了|的|在|里|从|保质期|个|瓶|支|袋|包|斤|公斤|kg|，|。| )"), " ")
        
        if (text.contains("是")) {
            val parts = text.split("是", limit = 2)
            // Name is extracted from skeleton mapping to text BEFORE "是"
            val beforeIsText = parts[0]
            var namePart = beforeIsText
            namePart = namePart.replace(Regex("(买|买了|了|的| )"), " ").trim()
            
            // Further clean if any entities were stripped from namePart
            // We use the same stripping logic but limited to this substring
            result.name = namePart.split(Regex("[，。！、\\s]+")).firstOrNull { it.length >= 2 } ?: "未知食品"
            
            // Remark is extracted from text AFTER "是" but cleaned of entities
            var remarkPart = parts[1]
            // Strip entities from remark if they haven't been stripped
            remarkPart = remarkPart.split(Regex("(分成了|放在|保质期|，|。)"))[0].trim()
            result.remark = remarkPart
        } else {
            // Find the most likely food candidate in the skeleton
            val words = skeleton.split(Regex("\\s+")).filter { it.length >= 2 }
            result.name = words.firstOrNull() ?: "未知食品"
            result.remark = if (words.size > 1) words.drop(1).joinToString(" ") else ""
        }

        // 3. Final cleanup for Food Name
        result.name = result.name.removeSuffix("的").removePrefix("买了").trim()
        if (result.name.isEmpty()) result.name = "未知食品"
        
        // 4. Heuristic Category assignment
        val meatKeywords = listOf("肉", "排骨", "鸡", "鱼", "虾", "蟹")
        val vegKeywords = listOf("菜", "瓜", "豆", "茄", "椒")
        val drinkKeywords = listOf("奶", "汁", "水", "酒", "汽水")
        
        when {
            meatKeywords.any { result.name.contains(it) } -> result.category = "肉蛋水产"
            vegKeywords.any { result.name.contains(it) } -> result.category = "蔬菜水果"
            drinkKeywords.any { result.name.contains(it) } -> result.category = "奶品饮料"
        }

        // Safety: If name is too long, it's likely a sentence that failed parsing
        if (result.name.length > 10) {
            result.name = result.name.take(10)
        }

        return result
    }
}
