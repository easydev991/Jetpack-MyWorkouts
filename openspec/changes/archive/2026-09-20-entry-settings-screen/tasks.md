# Tasks

## 1. Домен и локализация

- [x] 1.1 Добавить 12 строк в `app/src/main/res/values/strings.xml` (ru — дефолт) и `values-en/strings.xml` (en): `entry_settings_title` — Настроить экран / Configure screen; `entry_settings_subtitle` — Скрывайте ненужные поля / Hide fields you don't need; `entry_settings_content_description` — Настроить экран / Configure screen; `entry_visibility_enable_all` — Включить все / Enable all; `entry_visibility_sets_label` — Подходы / Sets; `entry_visibility_sets_hint` — По умолчанию всегда 1 подход / Defaults to 1 set; `entry_visibility_weight_label` — Доп. вес / Extra weight; `entry_visibility_date_label` — Дата / Date; `entry_visibility_date_hint` — По умолчанию дата открытия экрана / Defaults to today's date; `entry_visibility_time_label` — Время / Time; `entry_visibility_time_hint` — По умолчанию время открытия экрана / Defaults to the current time; `entry_visibility_comment_label` — Комментарий / Comment (апострофы в en-строках экранировать `\'`); проверка для обоих файлов: `rtk grep -o "entry_settings_[a-z_]*\|entry_visibility_[a-z_]*" <файл> | sort -u | wc -l` → 12 и `… | sort | uniq -d` → пусто (`grep -c` считает строки, не уникальные ключи; `make lint` новые строки без референсов не проверяет)
- [x] 1.2 Создать `app/src/main/java/com/myworkouts/domain/model/EntryField.kt` (SETS/WEIGHT/DATE/TIME/COMMENT, `@StringRes labelResId`, `hintResId: Int? = null` — по design D1); сборка `./gradlew compileDebugKotlin` проходит
- [x] 1.3 `navigation/Screen.kt`: `object EntrySettings : Screen(route = "entry_settings")` рядом с Appearance/AppData (до задачи 6.1 — иначе IconButton из 6.1 не компилируется); `./gradlew compileDebugKotlin` проходит

## 2. DataStore: видимость полей (TDD)

- [x] 2.1 Тест: в `AppSettingsDataStoreTest` добавить кейсы `entryFieldVisibility` — дефолт все true; `setFieldVisible(SETS, false)` → map `SETS == false`, остальные true; persistence повторным чтением Flow (extension `newDataStore`, `backgroundScope`); прогон `./gradlew testDebugUnitTest --tests "*AppSettingsDataStoreTest*"` — падает (API нет)
- [x] 2.2 Реализация: в `AppSettingsDataStore` добавить `fieldKey()`, `entryFieldVisibility: Flow<Map<EntryField, Boolean>>` (один `data.map`, дефолт `?: true`), `suspend fun setFieldVisible(field, visible)` (один `edit`); имя файла `appearance_prefs` не менять; прогон теста из 2.1 — зелёный

## 3. EntrySettingsViewModel (TDD)

- [x] 3.1 Тест: создать `ui/viewmodel/EntrySettingsViewModelTest.kt` — DataStore через `mockk(relaxed = true)` + `every { entryFieldVisibility } returns MutableStateFlow(...)`, `FakeAnalyticsProvider`; кейсы: дефолт (все видимы, `hasAnyDisabled == false`); `setFieldVisible(WEIGHT, false)` → map + событие `TOGGLE_ENTRY_FIELD_VISIBILITY` со значением `weight_off`; `enableAll()` → один `ENABLE_ALL_ENTRY_FIELDS` (value null) и ноль toggle-ивентов; последовательные emissions Flow → корректный финальный state; прогон — падает
- [x] 3.2 Аналитика: в `analytics/AnalyticsEvent.kt` добавить `UserActionType.TOGGLE_ENTRY_FIELD_VISIBILITY`, `UserActionType.ENABLE_ALL_ENTRY_FIELDS`, `AppScreen.ENTRY_SETTINGS`; компиляция теста 3.1 проходит (сам тест ещё красный по логике VM)
- [x] 3.3 Реализация: `ui/state/EntrySettingsUiState.kt` (data class `visibility: Map<EntryField, Boolean>`, computed `hasAnyDisabled`); `ui/viewmodel/EntrySettingsViewModel.kt` — `factory(dataStore, analyticsProvider)`, `setFieldVisible` (analytics value `"${field.name.lowercase()}_${on|off}"` → DataStore, прямой suspend в `viewModelScope.launch`, без Result), приватный `setFieldVisibleDataOnly` (запись без аналитики), `enableAll()` (один ивент до цикла `forEach { setFieldVisibleDataOnly(...) }`, внутри цикла без ивентов); прогон теста 3.1 — зелёный

## 4. Видимость в EntryCreate (TDD)

- [x] 4.1 Тест: в `EntryCreateViewModelTest` в setup добавить stub `every { dataStore.entryFieldVisibility } returns MutableStateFlow(все true)` (иначе combine не эмитит — design Risks); кейс: `entryFieldVisibility = { SETS: false }` → `isSetsFieldVisible == false`, остальные true, значения полей (setsText и др.) не меняются; в `EntryCreateUiStateTest` — `canSave == true` при скрытых опциональных полях без сброса значений; прогон — падает
- [x] 4.2 Реализация: `EntryCreateUiState` + 5 флагов `isXxxFieldVisible = true`; `EntryCreateViewModel` — параметр `dataStore: AppSettingsDataStore` в конструкторе и factory, combine расширен `entryFieldVisibility` с маппингом `EntryField` → флаг; без логики сброса значений; `canSave`/`hasUnsavedChanges`/`createModeChanges` не менять; сразу обновить call-site в `RootScreen.kt` — `EntryCreateNavDestination` принимает `dataStore` и вызывает `factory(repository, entryId, dataStore, analyticsProvider)` (иначе main-исходники не компилируются и `make test` падает); прогон тестов 4.1 и всего `make test` — зелёный

## 5. UI EntrySettingsScreen

- [x] 5.1 Создать `ui/screens/entry/EntrySettingsScreen.kt`: публичная composable `EntrySettingsScreen(viewModel, onBackClick)` по паттерну `AppearanceScreen` (collectAsState → состояние) + плоский `EntrySettingsScreenState` (поле `visibility` + лямбды `onToggleField`/`onEnableAll`/`onBackClick`, 1-в-1 с `EntryCreateScreen.kt:81`/`AppearanceScreen.kt:81`, без методов) + `internal fun EntrySettingsScreenContent(state: EntrySettingsScreenState)` (Scaffold, TopAppBar с ArrowBack + `R.string.back`, title `entry_settings_title`), подзаголовок, скроллящийся Column с `EntryField.entries.forEach { FieldToggleRow(...) }` (Row SpaceBetween/CenterVertically, Text weight(1f) + Switch, hint `bodySmall`; видимость поля — `visibility[field] ?: true`), TextButton «Включить все» при `hasAnyDisabled` с `navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)` — без per-field if-ов
- [x] 5.2 Превью: `EntrySettingsScreenPreview.kt` — 2 состояния (все включены без кнопки; часть выключена с кнопкой и хинтами), светлая + тёмная (`UI_MODE_NIGHT_YES`), `showBackground = true`, `locale = "ru"`, обёртка `JetpackMyWorkoutsTheme`; сборка и визуальная проверка превью в IDE/скриншотом

## 6. EntryCreateScreen: кнопка и условия рендера

- [x] 6.1 В `EntryCreateForm` добавить `&& uiState.isXxxFieldVisible` к рендеру подходов, веса, даты, времени, комментария (упражнение и типозависимое поле — без условий); IconButton `Icons.Filled.Settings` в `actions` TopAppBar с `contentDescription = R.string.entry_settings_content_description`, onClick — колбэк `onOpenSettings` (новый параметр `EntryCreateScreen`/`EntryCreateScreenState`); в этом же шаге обновить call-site — `EntryCreateNavDestination` (RootScreen.kt) передаёт `onOpenSettings = { navController.navigate(Screen.EntrySettings.route) }` (иначе main не компилируется и 6.2 падает)
- [x] 6.2 Прогнать `make test` — вся JVM-сюита зелёная после изменений формы

## 7. Навигация и RootScreen

- [x] 7.1 `RootScreen.kt`: `composable(Screen.EntrySettings.route)` → отдельная private `EntrySettingsNavDestination(navController, dataStore, analyticsProvider)` (detekt LongMethod); `screenForRoute` + `Screen.EntrySettings.route -> AppScreen.ENTRY_SETTINGS`; factory `EntrySettingsViewModel.factory(dataStore, analyticsProvider)` inline (call-site `EntryCreateViewModel` уже обновлён в 4.2, колбэк `onOpenSettings` — в 6.1)
- [x] 7.2 Проверить изоляцию точки входа, три раздельные проверки: (а) `rtk grep -n "EntrySettings" app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt` → пусто; (б) `rtk grep -n "EntrySettings" app/src/main/java/com/myworkouts/navigation/Screen.kt` → ровно одна строка — объявление `object EntrySettings` в sealed class (в списке `BottomTabs` вхождений нет); (в) `rtk grep -n "entry_settings" app/src/main/AndroidManifest.xml` → пусто; в остальном продакшен-коде хиты только в RootScreen.kt, EntryCreateScreen.kt, EntrySettings* (Screen/ViewModel/UiState/Preview — имена классов); `make lint` — detekt без новых предупреждений (лимит 5 не повышать)

## 8. androidTest

- [x] 8.1 Создать `androidTest/.../entry/EntrySettingsScreenTest.kt` (Content без VM, паттерн AppearanceScreenTest): рендер 5 тогглов + подзаголовок + TopAppBar; `onNodeWithText("Включить все")` doesNotExist при всех включённых / exists при выключенном; тап Switch инвертирует состояние через колбэк; тап «Включить все» — ровно один вызов `onEnableAll`
- [x] 8.2 Обновить `androidTest/.../entry/EntryCreateScreenTest.kt`: IconButton «Настроить экран» в TopAppBar + тап вызывает колбэк навигации; при `isWeightFieldVisible = false` поле «Доп. вес» не отображается; нон-регресс при `isWeightFieldVisible = true`
- [x] 8.3 `make android-test` (эмулятор подключён, один флейвор) — вся сюита зелёная

## 9. Финал

- [x] 9.1 `make format && make lint && make test` — всё зелёное (per AGENTS.md); detekt ≤ 5 issues без новых
- [x] 9.2 Ручная проверка на эмуляторе: открыть запись → тап шестерёнки → выключить «Комментарий» и «Дата» → назад → поля скрыты, значения остальных сохраняются; нижняя навигация приложения скрыта, системная панель навигации видна и не перекрыта; перезапуск приложения — настройки применились; кнопка «Включить все» возвращает поля (скриншоты в папке теста)
