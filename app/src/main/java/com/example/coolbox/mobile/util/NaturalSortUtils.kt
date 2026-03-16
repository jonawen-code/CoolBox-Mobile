package com.example.coolbox.mobile.util

import java.util.regex.Pattern

object NaturalSortUtils {
    private val PATTERN = Pattern.compile("(\\d+)|(\\D+)")

    /**
     * Sorts a list of strings naturally (e.g., "Layer 2" before "Layer 10").
     */
    fun List<String>.sortedNaturally(): List<String> {
        return this.sortedWith(NaturalComparator())
    }

    class NaturalComparator : Comparator<String> {
        override fun compare(s1: String?, s2: String?): Int {
            if (s1 == null || s2 == null) return (s1 ?: "").compareTo(s2 ?: "")
            
            // Step 1: Normalize Chinese digits for comparison logic
            val n1Final = normalizeToDigit(s1)
            val n2Final = normalizeToDigit(s2)
            
            val m1 = PATTERN.matcher(n1Final)
            val m2 = PATTERN.matcher(n2Final)

            while (m1.find() && m2.find()) {
                val g1 = m1.group()
                val g2 = m2.group()

                if (Character.isDigit(g1[0]) && Character.isDigit(g2[0])) {
                    try {
                        val v1 = g1.toLong()
                        val v2 = g2.toLong()
                        if (v1 != v2) return v1.compareTo(v2)
                    } catch (e: Exception) {
                        val res = g1.compareTo(g2, ignoreCase = true)
                        if (res != 0) return res
                    }
                } else {
                    val res = g1.compareTo(g2, ignoreCase = true)
                    if (res != 0) return res
                }
            }
            return n1Final.length.compareTo(n2Final.length)
        }
    }
    
    /**
     * Helper to normalize Chinese digits or strings for sorting (simple version).
     */
    fun normalizeForSort(text: String): String {
        return text.replace("一", "1")
            .replace("二", "2")
            .replace("三", "3")
            .replace("四", "4")
            .replace("五", "5")
            .replace("六", "6")
            .replace("七", "7")
            .replace("八", "8")
            .replace("九", "9")
            .replace("十", "10")
    }

    /**
     * Helper to normalize Chinese digits or strings to Arabic digits (V3.0.0 logic).
     */
    fun normalizeToDigit(s: String): String {
        var result = s.replace("第", "")
                      .replace("室", "")
                      .replace("层", "")

        val composites = listOf(
            "十二" to "12", "十三" to "13", "十四" to "14", "十五" to "15",
            "十六" to "16", "十七" to "17", "十八" to "18", "十九" to "19",
            "二十" to "20", "十一" to "11", "十" to "10"
        )
        for ((cn, digit) in composites) {
            result = result.replace(cn, digit)
        }

        val singles = listOf(
            "一" to "1", "二" to "2", "三" to "3", "四" to "4",
            "五" to "5", "六" to "6", "七" to "7", "八" to "8", "九" to "9"
        )
        for ((cn, digit) in singles) {
            result = result.replace(cn, digit)
        }
        return result
    }

    /**
     * Enforces the [Device] - [Layer] format and normalizes the layer digits.
     */
    fun normalizeHierarchyFormat(fridgeName: String): String {
        if (fridgeName.isBlank()) return fridgeName
        
        // 1. Ensure " - " separator
        var formatted = if (fridgeName.contains(" - ")) {
            fridgeName
        } else {
            // Re-normalize from "Device Layer" to "Device - Layer"
            val digitIndex = fridgeName.indexOfFirst { it.isDigit() || "一二三四五六七八九十".contains(it) }
            if (digitIndex > 0) {
                "${fridgeName.substring(0, digitIndex).trim()} - ${fridgeName.substring(digitIndex).trim()}"
            } else {
                fridgeName
            }
        }

        // 2. Normalize the layer part
        if (formatted.contains(" - ")) {
            val parts = formatted.split(" - ")
            if (parts.size >= 2) {
                val device = parts[0].trim()
                val layer = normalizeToDigit(parts[1].trim())
                return "$device - ${layer}层"
            }
        }
        return formatted
    }
}
