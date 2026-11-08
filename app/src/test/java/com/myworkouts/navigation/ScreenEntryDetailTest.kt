package com.myworkouts.navigation

import androidx.navigation.NavType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Тесты маршрута экрана деталей записи (workout-entry-detail-screen,
 * задача 4.1, design Decision 1): path-аргумент LongType без
 * defaultValue — экран всегда открывается с конкретным id записи.
 */
class ScreenEntryDetailTest {
    @Test
    fun `createRoute returns route with entry id`() {
        assertEquals("entry_detail/7", Screen.EntryDetail.createRoute(7L))
    }

    @Test
    fun `object is sealed Screen with route only`() {
        val obj: Screen = Screen.EntryDetail
        assertEquals(true, obj is Screen)
        assertEquals("entry_detail/{entryId}", obj.route)
    }

    @Test
    fun `arguments has long type without default value`() {
        val named = Screen.EntryDetail.arguments.single()
        val argument = named.argument

        assertEquals(Screen.EntryDetail.ARG_ENTRY_ID, named.name)
        assertEquals(NavType.LongType, argument.type)
        // NavArgument не отдаёт defaultValue наружу — отсутствие дефолта
        // фиксирует флаг isDefaultValuePresent
        assertFalse(argument.isDefaultValuePresent)
    }

    @Test
    fun `arg entry id constant equals route placeholder`() {
        assertEquals("entryId", Screen.EntryDetail.ARG_ENTRY_ID)
    }
}
