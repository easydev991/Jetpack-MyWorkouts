package com.myworkouts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myworkouts.navigation.ACTION_ADD_WORKOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as androidComposeTestRuleV2

/**
 * Интеграционные тесты статического шортката лаунчера «Добавить
 * тренировку» (openspec add-workout-quick-action): холодный старт по
 * intent шортката, guard от дублирования уже открытой формы,
 * пересоздание Activity (повторная доставка launch-intent) и
 * объявление шортката в [ShortcutManager].
 *
 * Правило запускает [MainActivity] с intent шортката (v2-фабрика
 * `createAndroidComposeRule` не принимает готовый intent — собираем
 * [AndroidComposeTestRule] с [ActivityScenarioRule] напрямую, как
 * рекомендует документация androidx).
 */
@RunWith(AndroidJUnit4::class)
class LauncherShortcutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun shortcutIntent(): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_ADD_WORKOUT
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    @get:Rule
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        androidComposeTestRuleV2(
            activityRule = ActivityScenarioRule(shortcutIntent()),
            activityProvider = { rule ->
                var activity: MainActivity? = null
                rule.scenario.onActivity { activity = it }
                checkNotNull(activity) { "Activity не запущена в ActivityScenarioRule" }
            }
        )

    @Test
    fun coldStart_whenShortcutIntent_thenOpensEntryCreate_andBackReturnsToMain() {
        // Given — правило запустило приложение с intent шортката

        // Then — открыт экран «Новая запись»
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_create_title))
            .assertExists()

        // When — «назад» из формы
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — главный экран (FAB «Добавить тренировку»)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .assertIsDisplayed()
    }

    @Test
    fun redelivery_whenFormAlreadyOpen_thenNoDuplicateForm_andTextPreserved_andSameActivity() {
        // Given — форма открыта по шорткату, в поле комментария введён текст
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_comment))
            .performTextInput("проверка шортката")
        var firstActivity: MainActivity? = null
        composeTestRule.activityRule.scenario.onActivity { firstActivity = it }

        // When — повторная доставка intent шортката (singleTop → onNewIntent)
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.startActivity(shortcutIntent())
        }
        composeTestRule.waitForIdle()

        // Then — форма одна (не дублируется поверх), текст сохранился,
        // второй экземпляр Activity не создавался
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_create_title))
            .assertCountEquals(1)
        composeTestRule
            .onNodeWithText("проверка шортката")
            .assertExists()
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assertSame("Второй экземпляр Activity не должен создаваться", firstActivity, activity)
        }
    }

    @Test
    fun recreation_afterFormClosed_thenFormDoesNotReopen() {
        // Given — форма открыта по шорткату и закрыта «назад»
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .assertIsDisplayed()

        // When — пересоздание Activity: платформа доставляет тот же
        // launch-intent шортката повторно (поворот, смена темы)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Then — форма самовольно не открылась, остался главный экран
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_create_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .assertIsDisplayed()
    }

    @Test
    fun staticShortcuts_whenQueried_thenContainsAddWorkout_withIcon() {
        // Given
        // When
        val shortcuts =
            ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_MANIFEST
            )

        // Then — шорткат объявлен, подпись из строки add_workout
        val shortcut = shortcuts.firstOrNull { it.id == SHORTCUT_ID_ADD_WORKOUT }
        assertNotNull("Статический шорткат add_workout должен быть объявлен", shortcut)
        shortcut ?: return
        assertEquals(
            "Подпись шортката должна браться из строки add_workout",
            context.getString(R.string.add_workout),
            shortcut.shortLabel.toString()
        )
        // Иконку манифестного шортката публичный ShortcutInfo.icon не отдаёт
        // (всегда null — иконка хранится resource-reference), поэтому факт
        // иконки проверяем по цепочке манифест → shortcuts.xml → android:icon.
        val activityInfo =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                PackageManager.GET_META_DATA
            )
        val shortcutsXmlRes =
            activityInfo.metaData
                ?.getInt(METADATA_KEY_SHORTCUTS) ?: 0
        assertTrue(
            "meta-data android.app.shortcuts должно указывать на shortcuts.xml",
            shortcutsXmlRes != 0
        )
        // Полный проход по shortcuts.xml: иконка шортката и action его
        // intent должны совпадать с Kotlin-константой ACTION_ADD_WORKOUT —
        // иначе шорткат молча открывает приложение на списке, а тесты,
        // строящие intent из константы, ничего не заметят.
        val parser = context.resources.getXml(shortcutsXmlRes)
        var iconResId = 0
        var shortcutAction: String? = null
        var insideShortcut = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "shortcut" -> {
                    insideShortcut = true
                    iconResId = parser.getAttributeResourceValue(ANDROID_NS, "icon", 0)
                }
                "intent" ->
                    if (insideShortcut) {
                        shortcutAction = parser.getAttributeValue(ANDROID_NS, "action")
                    }
            }
        }
        parser.close()
        assertEquals(
            "Шорткат add_workout должен ссылаться на иконку ic_shortcut_add_workout",
            R.drawable.ic_shortcut_add_workout,
            iconResId
        )
        assertEquals(
            "Action intent в shortcuts.xml должен совпадать с ACTION_ADD_WORKOUT",
            ACTION_ADD_WORKOUT,
            shortcutAction
        )
        // Фильтр в манифесте тоже должен ловить именно наш action
        // (category DEFAULT входит в MATCH_DEFAULT_ONLY).
        val resolves =
            context.packageManager.queryIntentActivities(
                Intent(ACTION_ADD_WORKOUT).setPackage(context.packageName),
                PackageManager.MATCH_DEFAULT_ONLY
            )
        assertTrue(
            "Intent-filter шортката в манифесте должен соответствовать ACTION_ADD_WORKOUT",
            resolves.any { it.activityInfo.name == MainActivity::class.java.name }
        )
    }

    private companion object {
        /** Id статического шортката из res/xml/shortcuts.xml. */
        const val SHORTCUT_ID_ADD_WORKOUT = "add_workout"

        /** Ключ meta-data шорткатов в манифесте. */
        const val METADATA_KEY_SHORTCUTS = "android.app.shortcuts"

        /** Namespace android-атрибутов в скомпилированном XML. */
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
