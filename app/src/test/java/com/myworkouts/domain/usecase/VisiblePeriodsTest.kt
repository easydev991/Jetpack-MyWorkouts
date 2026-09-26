package com.myworkouts.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.stream.Stream

/**
 * Unit-тесты чистых функций видимости периодов (overview-periods-and-shares,
 * tasks 1.1): пороги по дате самой ранней тренировки. Канон значений —
 * дельта спеки overview-screen (сценарии «Видимость периодов по глубине
 * истории», «Только сегодняшняя история», «Видимость периодов хитмапа по
 * глубине истории»); `today` — параметр, даты фиксированные.
 */
class VisiblePeriodsTest {
    /** Фиксированный «сегодня» — без зависимости от системных часов. */
    private val today: LocalDate = LocalDate.of(2026, 9, 26)

    companion object {
        /** `@MethodSource` требует static. */
        @JvmStatic
        fun statsPeriodCases(): Stream<Arguments> =
            Stream.of(
                // null — пустая БД (защитный случай)
                Arguments.of(Named.of("нет записей — только «День»", null), listOf(StatsPeriod.DAY)),
                // самая ранняя тренировка сегодня; «Неделя» требует истории
                // строго раньше сегодняшнего дня (граница: earliest == today)
                Arguments.of(Named.of("минус 0 дней — только «День»", 0L), listOf(StatsPeriod.DAY)),
                // ровно порог «Недели»
                Arguments.of(
                    Named.of("минус 1 день — «День» и «Неделя»", 1L),
                    listOf(StatsPeriod.DAY, StatsPeriod.WEEK)
                ),
                // за день до порога «Месяца»
                Arguments.of(Named.of("минус 6 дней — «Месяц» скрыт", 6L), listOf(StatsPeriod.DAY, StatsPeriod.WEEK)),
                // ровно порог «Месяца» (граница включена)
                Arguments.of(
                    Named.of("ровно минус 7 дней — «Месяц» виден", 7L),
                    listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH)
                ),
                // spec-сценарий: «сегодня минус 10 дней»
                Arguments.of(
                    Named.of("минус 10 дней — без «Года»", 10L),
                    listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH)
                ),
                // за день до порога «Года»
                Arguments.of(
                    Named.of("минус 29 дней — «Год» скрыт", 29L),
                    listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH)
                ),
                // ровно порог «Года» — видны все четыре периода
                Arguments.of(
                    Named.of("ровно минус 30 дней — видны все", 30L),
                    listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR)
                )
            )

        /** `@MethodSource` требует static. */
        @JvmStatic
        fun activityPeriodCases(): Stream<Arguments> =
            Stream.of(
                // null — пустая БД (защитный случай)
                Arguments.of(Named.of("нет записей — только «Месяц»", null), listOf(ActivityPeriod.MONTH)),
                // самая ранняя тренировка сегодня
                Arguments.of(Named.of("минус 0 дней — только «Месяц»", 0L), listOf(ActivityPeriod.MONTH)),
                // за день до порога «Квартала»
                Arguments.of(Named.of("минус 29 дней — «Квартал» скрыт", 29L), listOf(ActivityPeriod.MONTH)),
                // ровно порог «Квартала» (граница включена)
                Arguments.of(
                    Named.of("ровно минус 30 дней — «Квартал» виден", 30L),
                    listOf(ActivityPeriod.MONTH, ActivityPeriod.QUARTER)
                ),
                // spec-сценарий: «сегодня минус 40 дней»
                Arguments.of(
                    Named.of("минус 40 дней — без «Полугода» и «Года»", 40L),
                    listOf(ActivityPeriod.MONTH, ActivityPeriod.QUARTER)
                ),
                // за день до порога «Полугода»
                Arguments.of(
                    Named.of("минус 90 дней — «Полгода» скрыт", 90L),
                    listOf(ActivityPeriod.MONTH, ActivityPeriod.QUARTER)
                ),
                // ровно порог «Полугода» (граница включена)
                Arguments.of(
                    Named.of("ровно минус 91 день — «Полгода» виден", 91L),
                    listOf(ActivityPeriod.MONTH, ActivityPeriod.QUARTER, ActivityPeriod.HALFYEAR)
                ),
                // за день до порога «Года» хитмапа
                Arguments.of(
                    Named.of("минус 181 день — «Год» скрыт", 181L),
                    listOf(ActivityPeriod.MONTH, ActivityPeriod.QUARTER, ActivityPeriod.HALFYEAR)
                ),
                // ровно порог «Года» хитмапа — видны все четыре, порядок enum сохранён
                Arguments.of(
                    Named.of("ровно минус 182 дня — видны все", 182L),
                    listOf(
                        ActivityPeriod.MONTH,
                        ActivityPeriod.QUARTER,
                        ActivityPeriod.HALFYEAR,
                        ActivityPeriod.YEAR
                    )
                )
            )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statsPeriodCases")
    fun visible_stats_periods_match_days_since_first_entry(
        daysAgo: Long?,
        expected: List<StatsPeriod>
    ) {
        // When — earliest = «сегодня» минус daysAgo (null — записей нет)
        val periods = visibleStatsPeriods(earliest = daysAgo?.let(today::minusDays), today = today)

        // Then — периоды в порядке enum, границы порогов включены
        assertEquals(expected, periods, "Видимость stats-периодов по глубине истории")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activityPeriodCases")
    fun visible_activity_periods_match_days_since_first_entry(
        daysAgo: Long?,
        expected: List<ActivityPeriod>
    ) {
        // When — earliest = «сегодня» минус daysAgo (null — записей нет)
        val periods = visibleActivityPeriods(earliest = daysAgo?.let(today::minusDays), today = today)

        // Then — периоды в порядке enum, границы порогов включены
        assertEquals(expected, periods, "Видимость activity-периодов по глубине истории")
    }
}
