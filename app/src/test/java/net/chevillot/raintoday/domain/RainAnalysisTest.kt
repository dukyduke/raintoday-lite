package net.chevillot.raintoday.domain

import net.chevillot.raintoday.data.AlertRule
import net.chevillot.raintoday.data.RainSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RainAnalysisTest {

    private val zone = ZoneId.of("Europe/Paris")

    /** Série de 61 échantillons à 1 min, démarrant 15 min avant `now`. */
    private fun series(now: Instant, values: List<Int>): RainSeries {
        val start = now.minusSeconds(15 * 60)
        val padded = values + List(61 - values.size) { 0 }
        return RainSeries(
            areaId = "UWZTEST",
            start = start,
            intervalSeconds = 60,
            domainMin = 0,
            domainMax = 100,
            avg = padded,
            min = padded,
            max = padded,
            createdAt = now,
        )
    }

    private fun dry() = List(61) { 0 }

    @Test
    fun drySeriesIsDry() {
        val now = Instant.parse("2026-11-25T12:00:00Z")
        val outlook = series(now, dry()).outlook(now)
        assertTrue(outlook.state is RainState.Dry)
        assertEquals(RainLevel.NONE, outlook.level)
        assertNull(outlook.firstRain)
    }

    @Test
    fun rainStartingLaterIsReportedInMinutes() {
        val now = Instant.parse("2026-11-25T12:00:00Z")
        // Index 15 = maintenant ; pluie à partir de l'index 30 (soit +15 min)
        val values = List(30) { 0 } + List(10) { 40 }
        val outlook = series(now, values).outlook(now)
        val state = outlook.state as RainState.Starting
        assertEquals(15, state.inMinutes)
        assertEquals(10, state.durationMinutes)
        assertEquals(RainLevel.LIGHT, outlook.level)
    }

    @Test
    fun rainAlreadyFallingIsRaining() {
        val now = Instant.parse("2026-11-25T12:00:00Z")
        val values = List(15) { 0 } + List(8) { 60 } + List(38) { 0 }
        val outlook = series(now, values).outlook(now)
        val state = outlook.state as RainState.Raining
        assertEquals(8, state.remainingMinutes)
        assertEquals(RainLevel.MODERATE, outlook.level)
    }

    @Test
    fun thresholdIsExactlyTwentyFivePercent() {
        val now = Instant.parse("2026-11-25T12:00:00Z")
        val below = List(15) { 0 } + List(5) { 24 } + List(41) { 0 }
        assertTrue(series(now, below).outlook(now).state is RainState.Dry)

        val at = List(15) { 0 } + List(5) { 25 } + List(41) { 0 }
        assertTrue(series(now, at).outlook(now).state is RainState.Raining)
    }

    @Test
    fun rainBetweenFindsFirstWetSampleInsideWindow() {
        val now = Instant.parse("2026-11-25T12:00:00Z")
        val s = series(now, List(30) { 0 } + List(10) { 50 })
        val windowStart = now.plusSeconds(600)
        val windowEnd = now.plusSeconds(1800)
        val hit = s.rainBetween(windowStart, windowEnd)
        assertNotNull(hit)
        assertTrue(!hit!!.isBefore(windowStart) && !hit.isAfter(windowEnd))
    }

    @Test
    fun rainBetweenReturnsNullWhenWindowIsBeyondForecast() {
        val now = Instant.parse("2026-11-25T12:00:00Z")
        val s = series(now, List(61) { 0 })
        assertNull(s.rainBetween(now.plusSeconds(3 * 3600), now.plusSeconds(4 * 3600)))
    }

    @Test
    fun alertWindowIncludesLeadMinutes() {
        val rule = AlertRule(
            id = "r",
            placeId = "p",
            daysMask = 0b1111111,
            startMinute = 7 * 60,
            endMinute = 8 * 60,
            leadMinutes = 15,
        )
        val day = LocalDate.of(2026, 11, 25)
        val (from, to) = rule.windowOn(day, zone)
        assertEquals(ZonedDateTime.of(2026, 11, 25, 6, 45, 0, 0, zone).toInstant(), from)
        assertEquals(ZonedDateTime.of(2026, 11, 25, 8, 0, 0, 0, zone).toInstant(), to)
    }

    @Test
    fun alertCoversOnlyEnabledDays() {
        val mondayRule = AlertRule(
            id = "r",
            placeId = "p",
            daysMask = 0b0000001, // lundi
            startMinute = 7 * 60,
            endMinute = 8 * 60,
            leadMinutes = 0,
        )
        val monday = ZonedDateTime.of(2026, 11, 23, 7, 30, 0, 0, zone).toInstant()
        val tuesday = ZonedDateTime.of(2026, 11, 24, 7, 30, 0, 0, zone).toInstant()
        assertTrue(mondayRule.covers(monday, zone))
        assertFalse(mondayRule.covers(tuesday, zone))
    }

    @Test
    fun alertWindowCrossingMidnightIsCovered() {
        val rule = AlertRule(
            id = "r",
            placeId = "p",
            daysMask = 0b1111111,
            startMinute = 23 * 60,
            endMinute = 1 * 60,
            leadMinutes = 0,
        )
        val atHalfPastMidnight = ZonedDateTime.of(2026, 11, 25, 0, 30, 0, 0, zone).toInstant()
        assertTrue(rule.covers(atHalfPastMidnight, zone))

        val atNoon = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone).toInstant()
        assertFalse(rule.covers(atNoon, zone))
    }

    @Test
    fun daysLabelSummarisesWeekdaysAndWeekend() {
        val weekdays = AlertRule("r", "p", 0b0011111, 0, 60)
        assertEquals("en semaine", weekdays.daysLabel())
        val weekend = AlertRule("r2", "p", 0b1100000, 0, 60)
        assertEquals("le week-end", weekend.daysLabel())
        val all = AlertRule("r3", "p", 0b1111111, 0, 60)
        assertEquals("tous les jours", all.daysLabel())
    }
}
