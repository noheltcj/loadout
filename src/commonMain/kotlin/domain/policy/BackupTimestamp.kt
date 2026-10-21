package domain.policy

private const val MILLISECONDS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L

// Constants of the days-to-civil-date conversion, which counts 400-year eras from 0000-03-01.
private const val DAYS_FROM_ERA_START_TO_EPOCH = 719_468L
private const val DAYS_PER_ERA = 146_097L
private const val YEARS_PER_ERA = 400L

private data class CivilDate(
    val year: Long,
    val month: Long,
    val day: Long,
)

/** For example `20261020T174205Z`, in UTC and without milliseconds. */
fun formatBackupTimestamp(epochMilliseconds: Long): String {
    val epochSeconds = epochMilliseconds.floorDiv(MILLISECONDS_PER_SECOND)
    val secondOfDay = epochSeconds.mod(SECONDS_PER_DAY)
    val date = civilDate(daysSinceEpoch = epochSeconds.floorDiv(SECONDS_PER_DAY))

    return buildString {
        append(date.year.zeroPadded(width = 4))
        append(date.month.zeroPadded())
        append(date.day.zeroPadded())
        append('T')
        append((secondOfDay / SECONDS_PER_HOUR).zeroPadded())
        append((secondOfDay % SECONDS_PER_HOUR / SECONDS_PER_MINUTE).zeroPadded())
        append((secondOfDay % SECONDS_PER_MINUTE).zeroPadded())
        append('Z')
    }
}

/** Follows Howard Hinnant's `civil_from_days` for the proleptic Gregorian calendar. */
private fun civilDate(daysSinceEpoch: Long): CivilDate {
    val daysSinceEraStart = daysSinceEpoch + DAYS_FROM_ERA_START_TO_EPOCH
    val era = daysSinceEraStart.floorDiv(DAYS_PER_ERA)
    val dayOfEra = daysSinceEraStart - era * DAYS_PER_ERA
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthFromMarch = (5 * dayOfYear + 2) / 153
    val month = if (monthFromMarch < 10) monthFromMarch + 3 else monthFromMarch - 9

    return CivilDate(
        year = yearOfEra + era * YEARS_PER_ERA + if (month <= 2) 1 else 0,
        month = month,
        day = dayOfYear - (153 * monthFromMarch + 2) / 5 + 1,
    )
}

private fun Long.zeroPadded(width: Int = 2): String = toString().padStart(width, '0')
