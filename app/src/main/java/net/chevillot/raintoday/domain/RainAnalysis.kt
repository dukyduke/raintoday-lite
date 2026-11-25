package net.chevillot.raintoday.domain

import net.chevillot.raintoday.data.AlertRule
import net.chevillot.raintoday.data.RainSeries
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Seuil de pluie utilisé par Rain Today (25 % de l'échelle du service). */
const val RAIN_THRESHOLD = 0.25f

enum class RainLevel { NONE, LIGHT, MODERATE, HEAVY }

sealed interface RainState {
    /** Aucune pluie prévue dans la fenêtre de prévision. */
    data object Dry : RainState
    /** Il pleut actuellement, encore N minutes. */
    data class Raining(val remainingMinutes: Int) : RainState
    /** Pluie dans N minutes, pendant M minutes. */
    data class Starting(val inMinutes: Int, val durationMinutes: Int) : RainState
}

data class Outlook(
    val state: RainState,
    val level: RainLevel,
    val peak: Float,
    val firstRain: Instant?,
    val lastRain: Instant?,
    val nowIndex: Int,
) {
    val isWet: Boolean get() = state !is RainState.Dry
}

fun RainSeries.outlook(now: Instant = Instant.now()): Outlook {
    val nowIndex = indexAt(now)
    if (size == 0) return Outlook(RainState.Dry, RainLevel.NONE, 0f, null, null, 0)

    var first = -1
    for (i in nowIndex until size) {
        if (fraction(avgAt(i)) >= RAIN_THRESHOLD) { first = i; break }
    }
    if (first < 0) return Outlook(RainState.Dry, RainLevel.NONE, 0f, null, null, nowIndex)

    var last = first
    while (last + 1 < size && fraction(avgAt(last + 1)) >= RAIN_THRESHOLD) last++

    var peak = 0f
    for (i in first..last) peak = maxOf(peak, fraction(avgAt(i)))
    val level = when {
        peak >= 0.75f -> RainLevel.HEAVY
        peak >= 0.50f -> RainLevel.MODERATE
        else -> RainLevel.LIGHT
    }

    val step = (intervalSeconds / 60L).toInt().coerceAtLeast(1)
    val state = if (first == nowIndex) {
        RainState.Raining((last - nowIndex + 1) * step)
    } else {
        RainState.Starting((first - nowIndex) * step, (last - first + 1) * step)
    }
    return Outlook(state, level, peak, timeAt(first), timeAt(last), nowIndex)
}

/** Premier instant de pluie entre deux bornes, ou null. */
fun RainSeries.rainBetween(from: Instant, to: Instant): Instant? {
    if (size == 0) return null
    if (from.isAfter(lastTime)) return null
    val a = indexAt(from)
    val b = indexAt(if (to.isAfter(lastTime)) lastTime else to)
    for (i in a..b) if (fraction(avgAt(i)) >= RAIN_THRESHOLD) return timeAt(i)
    return null
}

// --- Règles d'alerte -------------------------------------------------------

fun AlertRule.isEnabledOn(date: LocalDate): Boolean =
    daysMask and (1 shl (date.dayOfWeek.value - 1)) != 0

/** Fenêtre effective : [début - prévenance ; fin], en tenant compte du passage de minuit. */
fun AlertRule.windowOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
    val start = date.atTime(startMinute / 60, startMinute % 60).atZone(zone)
    var end = date.atTime(endMinute / 60, endMinute % 60).atZone(zone)
    if (!end.isAfter(start)) end = end.plusDays(1)
    return start.minusMinutes(leadMinutes.toLong()).toInstant() to end.toInstant()
}

/** La règle est-elle active maintenant (prévenance incluse) ? */
fun AlertRule.covers(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val today = now.atZone(zone).toLocalDate()
    for (day in listOf(today, today.minusDays(1))) {
        if (!isEnabledOn(day)) continue
        val (from, to) = windowOn(day, zone)
        if (!now.isBefore(from) && now.isBefore(to)) return true
    }
    return false
}

val DAY_LETTERS = listOf("L", "M", "M", "J", "V", "S", "D")

fun AlertRule.daysLabel(): String {
    val days = (0..6).filter { daysMask and (1 shl it) != 0 }
    return when {
        days.isEmpty() -> "jamais"
        daysMask == 0b0011111 -> "en semaine"
        daysMask == 0b1100000 -> "le week-end"
        daysMask == 0b1111111 -> "tous les jours"
        else -> days.joinToString(" ") { DAY_LETTERS[it] }
    }
}
