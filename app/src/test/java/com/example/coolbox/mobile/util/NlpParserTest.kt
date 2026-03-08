package com.example.coolbox.mobile.util

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class NlpParserTest {

    @Test
    fun parseSimpleSentence() {
        val baseTimeMs = Calendar.getInstance().apply { set(2026, 2, 5) }.timeInMillis // 2026-03-05
        
        val sentence = "两瓶山姆高钙牛奶放在大冰箱冷藏室，保质期到这个月底"
        val result = NlpParser.parse(sentence, baseTimeMs)
        
        assertEquals("牛奶", result.name)
        assertEquals(2.0, result.quantity, 0.01)
        assertEquals("瓶", result.unit)
        assertEquals("大冰箱冷藏室", result.location)
    }

    @Test
    fun testComplexUserCaseV6() {
        val baseTimeMs = Calendar.getInstance().apply { set(2026, 2, 6) }.timeInMillis // 2026-03-06
        // Input from user: "我买了两斤猪肉，分成了两份，放在了冰柜的第三层，保质期算一周吧"
        val input = "我买了两斤猪肉，分成了两份，放在了冰柜的第三层，保质期算一周吧"
        val result = NlpParser.parse(input, baseTimeMs)
        
        println("V6 Case: Name='${result.name}', Qty=${result.quantity}, Unit='${result.unit}', Loc='${result.location}', Portions=${result.portions}, Expiry='${result.expiryMs}'")
        
        assertEquals("猪肉", result.name)
        assertEquals(2.0, result.quantity, 0.01)
        assertEquals("斤", result.unit)
        assertEquals(2, result.portions)
        assertEquals("冰柜的第三层", result.location) // Number should be PRESERVED
        
        // Expiry should be 7 days after 2026-03-06 -> 2026-03-13
        val expectedCal = Calendar.getInstance().apply { set(2026, 2, 13) }
        val resultCal = Calendar.getInstance().apply { timeInMillis = result.expiryMs }
        assertEquals(expectedCal.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(expectedCal.get(Calendar.YEAR), resultCal.get(Calendar.YEAR))
    }

    @Test
    fun testPortionUnitsAndDurations() {
        val baseTimeMs = Calendar.getInstance().apply { set(2026, 2, 6) }.timeInMillis
        val input = "买了三斤排骨，共五袋，放在冷冻室，保质期一年"
        val result = NlpParser.parse(input, baseTimeMs)
        
        assertEquals("排骨", result.name)
        assertEquals(5, result.portions)
        
        val expectedCal = Calendar.getInstance().apply { set(2027, 2, 6) }
        assertEquals(expectedCal.get(Calendar.YEAR), Calendar.getInstance().apply { timeInMillis = result.expiryMs }.get(Calendar.YEAR))
    }

    @Test
    fun testHalfYearDuration() {
        val baseTimeMs = Calendar.getInstance().apply { set(2026, 2, 6) }.timeInMillis
        val input = "两瓶牛奶键盘语音输入，保质期半年"
        val result1 = NlpParser.parse(input, baseTimeMs)
        
        assertEquals("牛奶", result1.name)
        assertEquals(2.0, result1.quantity, 0.01)
        assertEquals("瓶", result1.unit)
        
        val cal1 = Calendar.getInstance().apply { timeInMillis = result1.expiryMs }
        assertEquals(2026, cal1.get(Calendar.YEAR))
        assertEquals(9, cal1.get(Calendar.MONTH) + 1) // 3 + 6 = 9
        
        val input2 = "一斤牛肉，有半年"
        val result2 = NlpParser.parse(input2, baseTimeMs)
        assertEquals("牛肉", result2.name)
        val cal2 = Calendar.getInstance().apply { timeInMillis = result2.expiryMs }
        assertEquals(9, cal2.get(Calendar.MONTH) + 1)
        assertTrue(result2.remark.isEmpty())
    }

    @Test
    fun testIsoDate() {
        val input = "保质期到2026-10-15"
        val result = NlpParser.parse(input)
        val cal = Calendar.getInstance().apply { timeInMillis = result.expiryMs }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(10, cal.get(Calendar.MONTH) + 1)
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }
}
