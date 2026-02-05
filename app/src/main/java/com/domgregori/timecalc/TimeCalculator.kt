package com.domgregori.timecalc

data class Time(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0
) {
    fun toSeconds(): Long {
        return hours * 3600L + minutes * 60L + seconds
    }

    fun toFormattedString(): String {
        val totalSeconds = toSeconds()
        val sign = if (totalSeconds < 0) "-" else ""
        val absSeconds = kotlin.math.abs(totalSeconds)
        val absHours = (absSeconds / 3600).toInt()
        val absMinutes = ((absSeconds % 3600) / 60).toInt()
        val absSecs = (absSeconds % 60).toInt()
        return String.format("%s%02d:%02d:%02d", sign, absHours, absMinutes, absSecs)
    }

    companion object {
        fun fromSeconds(totalSeconds: Long): Time {
            val absSeconds = kotlin.math.abs(totalSeconds)
            val hours = (absSeconds / 3600).toInt()
            val minutes = ((absSeconds % 3600) / 60).toInt()
            val seconds = (absSeconds % 60).toInt()
            
            return if (totalSeconds < 0) {
                Time(-hours, -minutes, -seconds)
            } else {
                Time(hours, minutes, seconds)
            }
        }
    }
}

object TimeCalculator {
    fun add(time1: Time, time2: Time): Time {
        val totalSeconds = time1.toSeconds() + time2.toSeconds()
        return Time.fromSeconds(totalSeconds)
    }

    fun subtract(time1: Time, time2: Time): Time {
        val totalSeconds = time1.toSeconds() - time2.toSeconds()
        return Time.fromSeconds(totalSeconds)
    }

    fun multiply(time: Time, multiplier: Double): Time {
        val totalSeconds = (time.toSeconds() * multiplier).toLong()
        return Time.fromSeconds(totalSeconds)
    }

    fun divide(time: Time, divisor: Double): Time {
        if (divisor == 0.0) {
            return Time(0, 0, 0)
        }
        val totalSeconds = (time.toSeconds() / divisor).toLong()
        return Time.fromSeconds(totalSeconds)
    }
}
