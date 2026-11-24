package com.example.glassweather

import java.util.*
import kotlin.math.floor

object LunarCalendarHelper {
    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    fun getFullLunarString(cal: Calendar = Calendar.getInstance()): String {
        val dd = cal.get(Calendar.DAY_OF_MONTH)
        val mm = cal.get(Calendar.MONTH) + 1
        val yy = cal.get(Calendar.YEAR)

        val jd = jdFromDate(dd, mm, yy)
        val lunar = convertSolar2Lunar(dd, mm, yy, 7.0)

        val canDayIdx = ((jd + 9) % 10 + 10) % 10
        val chiDayIdx = ((jd + 1) % 12 + 12) % 12
        val dayCanChi = "${CAN[canDayIdx]} ${CHI[chiDayIdx]}"

        val canYearIdx = ((lunar[2] + 6) % 10 + 10) % 10
        val chiYearIdx = ((lunar[2] + 8) % 12 + 12) % 12
        val yearCanChi = "${CAN[canYearIdx]} ${CHI[chiYearIdx]}"

        return "Âm: ${lunar[0]}/${lunar[1]} • Ngày $dayCanChi • $yearCanChi"
    }

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = floor((14 - mm) / 12.0).toInt()
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + floor((153 * m + 2) / 5.0).toInt() + 365 * y +
                floor(y / 4.0).toInt() - floor(y / 100.0).toInt() + floor(y / 400.0).toInt() - 32045
        if (jd < 2299161) {
            jd = dd + floor((153 * m + 2) / 5.0).toInt() + 365 * y + floor(y / 4.0).toInt() - 32083
        }
        return jd
    }

    private fun getNewMoonDay(k: Int, timeZone: Double): Int {
        val T = k / 1236.85
        val T2 = T * T
        val T3 = T2 * T
        val dr = Math.PI / 180.0
        var Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3
        Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr)
        val M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3
        val Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3
        val F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3
        var C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M)
        C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr)
        C1 -= 0.0004 * Math.sin(dr * 3 * Mpr)
        C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr))
        C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M))
        C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr))
        C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M))
        val deltat = if (T < -11) 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
        else -0.000278 + 0.000265 * T + 0.000262 * T2
        return floor(Jd1 + C1 - deltat + 0.5 + timeZone / 24.0).toInt()
    }

    private fun getSunLongitude(jdn: Int, timeZone: Double): Int {
        val T = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0
        val T2 = T * T
        val dr = Math.PI / 180.0
        val M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2
        val L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2
        var DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M)
        DL += (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M)
        var L = (L0 + DL) * dr
        L -= Math.PI * 2 * floor(L / (Math.PI * 2))
        return floor(L / Math.PI * 6).toInt()
    }

    private fun getLunarMonth11(yy: Int, timeZone: Double): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, timeZone)
        if (getSunLongitude(nm, timeZone) >= 9) nm = getNewMoonDay(k - 1, timeZone)
        return nm
    }

    private fun getLeapMonthOffset(a11: Int, timeZone: Double): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        } while (arc != last && i < 14)
        return i - 1
    }

    fun convertSolar2Lunar(dd: Int, mm: Int, yy: Int, timeZone: Double): IntArray {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1, timeZone)
        if (monthStart > dayNumber) monthStart = getNewMoonDay(k, timeZone)
        var a11 = getLunarMonth11(yy, timeZone)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, timeZone)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, timeZone)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11, timeZone)
            if (diff >= leapMonthDiff) lunarMonth = diff + 10
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return intArrayOf(lunarDay, lunarMonth, lunarYear)
    }
}
