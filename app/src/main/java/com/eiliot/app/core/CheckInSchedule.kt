package com.eiliot.app.core

import java.time.LocalTime

/** Wall-clock schedule, independent of inactivity. A missed interval never becomes a backlog. */
data class CheckInSchedule(val startMinute: Int, val endMinute: Int, val intervalMinutes: Int) {
    init { require(startMinute in 0..1439 && endMinute in 0..1439 && intervalMinutes in 5..1440) }
    fun inside(time: LocalTime): Boolean {
        val minute = time.hour * 60 + time.minute
        return when { startMinute == endMinute -> true; startMinute < endMinute -> minute in startMinute until endMinute; else -> minute >= startMinute || minute < endMinute }
    }
    fun due(now: Long, last: Long, snoozeUntil: Long, localTime: LocalTime) = inside(localTime) && now >= snoozeUntil && now >= last && now - last >= intervalMinutes * 60000L
}
