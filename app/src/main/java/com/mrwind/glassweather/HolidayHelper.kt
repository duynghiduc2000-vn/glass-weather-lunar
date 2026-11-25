package com.mrwind.glassweather

import java.util.Calendar

object HolidayHelper {

    fun getTodayHolidayBanner(calendar: Calendar): String? {
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val solarHoliday = when (month to day) {
            1 to 1 -> "Tết Dương lịch"
            2 to 14 -> "Lễ Tình nhân (Valentine)"
            3 to 8 -> "Ngày Quốc tế Phụ nữ"
            4 to 30 -> "Ngày Giải phóng miền Nam"
            5 to 1 -> "Ngày Quốc tế Lao động"
            6 to 1 -> "Ngày Quốc tế Thiếu nhi"
            7 to 27 -> "Ngày Thương binh - Liệt sĩ"
            8 to 19 -> "Cách mạng Tháng Tám"
            9 to 2 -> "Quốc khánh Việt Nam"
            10 to 20 -> "Ngày Phụ nữ Việt Nam"
            10 to 31 -> "Halloween"
            11 to 19 -> "Ngày Quốc tế Nam giới"
            11 to 20 -> "Ngày Nhà giáo Việt Nam"
            12 to 22 -> "Ngày Thành lập QĐND Việt Nam"
            12 to 24 -> "Đêm Giáng sinh"
            12 to 25 -> "Giáng sinh"
            12 to 31 -> "Đêm giao thừa Dương lịch"
            else -> null
        }

        if (solarHoliday != null) {
            return "Hôm nay là $solarHoliday"
        }

        val (lunarDay, lunarMonth) = LunarCalendarHelper.getLunarDayMonth(calendar)
        val lunarHoliday = when (lunarMonth to lunarDay) {
            1 to 1 -> "Mùng 1 Tết Nguyên Đán"
            1 to 2 -> "Mùng 2 Tết Nguyên Đán"
            1 to 3 -> "Mùng 3 Tết Nguyên Đán"
            1 to 15 -> "Rằm tháng Giêng (Tết Nguyên tiêu)"
            3 to 10 -> "Giỗ Tổ Hùng Vương"
            4 to 15 -> "Lễ Phật Đản"
            5 to 5 -> "Tết Đoan Ngọ"
            7 to 15 -> "Vu Lan báo hiếu"
            8 to 15 -> "Tết Trung thu"
            12 to 23 -> "Ông Công Ông Táo"
            else -> null
        }

        return lunarHoliday?.let { "Hôm nay là $it" }
    }
}
