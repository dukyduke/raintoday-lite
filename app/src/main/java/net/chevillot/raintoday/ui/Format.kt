package net.chevillot.raintoday.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** "à 14:12" pour un horodatage, vide si jamais résolu. */
fun Long.frTime(): String =
    if (this <= 0L) "" else "à ${TIME.format(Instant.ofEpochMilli(this))}"
