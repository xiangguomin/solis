package dev.solis.tablet.data.calendar

data class CalendarEvent(
    val id: Long,
    val title: String,
    val timeRangeText: String,
    val dayGroupLabel: String,
    val calendarColorHex: Long,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val startTimeMillis: Long = System.currentTimeMillis()
) {
    companion object {
        fun samples() = listOf(
            CalendarEvent(
                id = 1,
                title = "Architecture Sync & Sprint Planning",
                timeRangeText = "10:30 AM – 11:30 AM",
                dayGroupLabel = "TODAY",
                calendarColorHex = 0xFF4EA8DE
            ),
            CalendarEvent(
                id = 2,
                title = "Dentist Appointment",
                timeRangeText = "2:00 PM – 3:00 PM",
                dayGroupLabel = "TODAY",
                calendarColorHex = 0xFFFFC857,
                location = "Sunnyvale Dental"
            ),
            CalendarEvent(
                id = 3,
                title = "Dinner with Family",
                timeRangeText = "6:30 PM – 8:00 PM",
                dayGroupLabel = "TODAY",
                calendarColorHex = 0xFF52B788
            ),
            CalendarEvent(
                id = 4,
                title = "Weekend Tahoe Trip",
                timeRangeText = "All Day",
                dayGroupLabel = "TOMORROW",
                calendarColorHex = 0xFFF28482,
                isAllDay = true
            )
        )
    }
}
