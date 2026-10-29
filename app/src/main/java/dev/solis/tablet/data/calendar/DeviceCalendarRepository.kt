package dev.solis.tablet.data.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reads upcoming events directly from the Lenovo tablet's synced Google Calendars
 * via Android's CalendarContract ContentProvider.
 */
class DeviceCalendarRepository(private val context: Context) {

    suspend fun queryUpcomingEvents(hoursAhead: Long = 48): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val nowMillis = System.currentTimeMillis()
            val endMillis = nowMillis + hoursAhead * 3600_000L

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, nowMillis)
            ContentUris.appendId(builder, endMillis)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION
            )

            val events = mutableListOf<CalendarEvent>()
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            val zone = ZoneId.systemDefault()

            context.contentResolver.query(
                builder.build(),
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < 30) {
                    val id = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "(No Title)"
                    val begin = cursor.getLong(2)
                    val end = cursor.getLong(3)
                    val allDay = cursor.getInt(4) == 1
                    val color = cursor.getInt(5).toLong() and 0xFFFFFFFFL
                    val location = cursor.getString(6)

                    val startZdt = Instant.ofEpochMilli(begin).atZone(zone)
                    val endZdt = Instant.ofEpochMilli(end).atZone(zone)
                    val timeRange = if (allDay) {
                        "All Day"
                    } else {
                        "${startZdt.format(timeFormatter)} – ${endZdt.format(timeFormatter)}"
                    }

                    events.add(
                        CalendarEvent(
                            id = id,
                            title = title,
                            timeRangeText = timeRange,
                            dayGroupLabel = startZdt.dayOfWeek.name.take(3),
                            calendarColorHex = if (color != 0L) color else 0xFF4EA8DE,
                            isAllDay = allDay,
                            location = location,
                            startTimeMillis = begin
                        )
                    )
                }
            }
            events
        }
}
