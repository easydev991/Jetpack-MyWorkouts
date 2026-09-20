# Design

## Context

`EntryCreateScreen` показывает фиксированный набор полей; обязательные (упражнение,
повторы/длительность по `measuredBy`) нельзя скрывать, 5 опциональных — можно.
Настройки приложения живут в `AppSettingsDataStore` (файл `appearance_prefs`,
DataStore singleton) по паттерну «один Flow на ключ». Экраны-настройки
(`AppearanceScreen`) задают паттерн: Scaffold + TopAppBar + Content-функция для
превью/androidTest. Manual DI: фабрики VM собираются в `RootScreen`. См. proposal.md
(Why) и спеки (What).

## Goals / Non-Goals

**Goals:**

- Enum-driven дизайн: добавление опционального поля = одна строка в `EntryField`
  + пара строк локализации; экран настроек, `EntrySettingsViewModel` и DataStore
  при этом не меняются (сторона формы — существующий паттерн флагов
  `EntryCreateUiState`: флаг, маппинг в combine, условие рендера — D3).
- Видимость без побочных эффектов: скрытие поля не трогает значение, `canSave`,
  `hasUnsavedChanges`/`createModeChanges`.
- Минимум нового кода: переиспользование паттернов `AppearanceScreen`/`EntryCreateScreen`.

**Non-Goals:**

- Управление обязательными полями (упражнение, повторы/длительность).
- Сброс значений скрытых полей (явно запрещено требованием).
- Точки входа на настройки кроме TopAppBar `EntryCreateScreen` (MoreScreen, BottomTabs,
  deep links — не трогаем).
- Crashlytics для тогглов (только аналитика).

## Decisions

### D1. `EntryField` — enum в domain с `@StringRes`

`domain/model/EntryField.kt`: `SETS/WEIGHT/DATE/TIME/COMMENT` с `labelResId` и
опциональным `hintResId` под `@StringRes`. `androidx.annotation.StringRes` —
аннотация времени компиляции, в рантайме стирается, домен остаётся без зависимости
от ресурсов.

*Альтернатива* — ресурс-маппинг расширением в `ui/screens/entry/`: отклонена —
размазывает определение поля по двум файлам и ломает «добавление поля = одна строка».
Если ревью потребует строгости, миграция механическая (перенос в extension), на спеки
не влияет.

Имена констант UPPER_SNAKE — `name.lowercase()` даёт сегменты DataStore-ключей.

Локализация: ru — дефолт `app/src/main/res/values/strings.xml`, en —
`values-en/strings.xml`; `values-ru` не создавать (миграционная ловушка;
раскладка values/values-en — навык localization).

### D2. Хранение: те же `appearance_prefs`, комбинированный Flow + параметризованный setter

- Ключ: `private fun fieldKey(field: EntryField) = booleanPreferencesKey("show_${field.name.lowercase()}_field")`.
- Имя файла `appearance_prefs` не меняется: DataStore singleton, второй файл потребовал
  бы второй инстанс.
- API: `val entryFieldVisibility: Flow<Map<EntryField, Boolean>>` — один `data.map` с
  `EntryField.entries.associateWith { prefs[fieldKey(it)] ?: true }` (не combine 5 Flow-ов);
  `suspend fun setFieldVisible(field: EntryField, visible: Boolean)` — один `edit`.
- Отступление от «один Flow на ключ» оправдано связанностью настроек и ростом enum:
  новые поля не добавляют методов.

### D3. Видимость в `EntryCreateUiState` — 5 флагов, combine в VM

- `EntryCreateUiState`: `isSetsFieldVisible/isWeightFieldVisible/isDateFieldVisible/
  isTimeFieldVisible/isCommentFieldVisible = true` (дефолт). Без флагов для
  упражнения/повторов-длительности.
- `EntryCreateViewModel`: конструктор получает `AppSettingsDataStore`; существующий
  combine UiState расширяется источником `dataStore.entryFieldVisibility` с маппингом
  `EntryField` → флаг. Никакой логики сброса значений.
- Форма: в `EntryCreateForm` условия рендера 5 опциональных блоков дополняются
  `&& uiState.isXxxFieldVisible`.
- **Внимание к тестам**: существующий `EntryCreateViewModelTest` в setup мокает
  DataStore через MockK `mockk(relaxed = true)`; после добавления источника в combine
  нужно явно `every { dataStore.entryFieldVisibility } returns MutableStateFlow(все true)`
  — иначе combine на пустом Flow не эмитит и тесты зависают.

### D4. Экран `EntrySettingsScreen` — итерация по enum, паттерн Appearance

- TopAppBar: title «Настроить экран», `navigationIcon` = `Icons.AutoMirrored.Filled.ArrowBack`
  с `contentDescription = R.string.back` (1-в-1 с AppearanceScreen); подзаголовок;
  скроллящийся Column; `EntryField.entries.forEach { FieldToggleRow(...) }`.
- `FieldToggleRow` (private): Row `SpaceBetween`/`CenterVertically` — Text
  (`Modifier.weight(1f)`) + Switch; ниже hint `bodySmall` если задан; отступы и
  safe-area (`navigationBarsPadding().imePadding()`) по паттерну SaveBottomBar
  `EntryCreateScreen`.
- Кнопка «Включить все» (`TextButton`) внизу — видна при `hasAnyDisabled`.
- State-паттерн как у `EntryCreateScreen`: `EntrySettingsUiState` (data class:
  `visibility: Map<EntryField, Boolean>`, computed `hasAnyDisabled`) в `ui/state/`;
  `EntrySettingsScreenState` — плоский data class в файле экрана, 1-в-1 с прецедентами
  (`EntryCreateScreenState` в `EntryCreateScreen.kt:81`, `AppearanceScreenState` в
  `AppearanceScreen.kt:81`): поле `visibility` + лямбды `onToggleField`, `onEnableAll`,
  `onBackClick`, без методов — видимость поля в Content вычисляется
  `visibility[field] ?: true`. Content-функция
  `internal fun EntrySettingsScreenContent(state: EntrySettingsScreenState)`
  — превью и androidTest минуют VM.
- Factory: `viewModel(factory = EntrySettingsViewModel.factory(dataStore, analyticsProvider))`
  inline в NavDestination — без новых DI-абстракций.

### D5. Навигация

- `object EntrySettings : Screen(route = "entry_settings")` в `Screen.kt` рядом с
  Appearance/AppData.
- `RootScreen`: `composable(Screen.EntrySettings.route)` → **отдельная** private
  `EntrySettingsNavDestination(navController, dataStore, analyticsProvider)` (вынос
  обязателен — RootScreen раздут, detekt LongMethod, лимит 5 не повышаем).
- `screenForRoute`: `Screen.EntrySettings.route -> AppScreen.ENTRY_SETTINGS`.
- Нижняя панель приложения скрывается автоматически: `shouldShowBottomBar = currentRoute in
  tabs.map { it.route }`, правки не нужны (как на `EntryCreate`). Системная панель
  навигации Android не затрагивается — insets обрабатывает собственный Scaffold экрана
  и `navigationBarsPadding()` на кнопке «Включить все» (D4).
- Кнопка входа: `Icons.Filled.Settings` в `actions` TopAppBar `EntryCreateScreen`,
  `contentDescription = R.string.entry_settings_content_description`; onClick — колбэк
  `onOpenSettings` (экран работает через лямбды, как `onBackClick`), в
  `EntryCreateNavDestination` подключаемый к `navController.navigate(Screen.EntrySettings.route)`.

### D6. Аналитика

- `UserActionType`: + `TOGGLE_ENTRY_FIELD_VISIBILITY`, `ENABLE_ALL_ENTRY_FIELDS`.
- `AppScreen`: + `ENTRY_SETTINGS`.
- `setFieldVisible(field, visible)` логирует `TOGGLE_ENTRY_FIELD_VISIBILITY` со
  значением `"${field.name.lowercase()}_${if (visible) "on" else "off"}"` (паттерн
  value `TOGGLE_EXERCISES_TAB`), затем пишет в DataStore (прямой suspend в
  `viewModelScope.launch`, без `Result` — паттерн AppearanceViewModel).
- `enableAll()` логирует ровно один `ENABLE_ALL_ENTRY_FIELDS` (value null) до цикла
  `EntryField.entries.forEach { setFieldVisibleDataOnly(...) }` — внутри цикла отдельных
  toggle-ивентов нет (аналитику не засоряем).

### D7. Тесты (TDD: JVM-тесты до реализации)

- `EntrySettingsViewModelTest` (новый): DataStore — `mockk(relaxed = true)` +
  `every { entryFieldVisibility } returns MutableStateFlow(...)` (паттерн
  AppearanceViewModelTest); `FakeAnalyticsProvider` уже есть в `app/src/test/.../analytics/`.
  Кейсы: дефолт все видимы/`hasAnyDisabled == false`; toggle → map + событие
  `("weight_off")`; `enableAll` → один `ENABLE_ALL_ENTRY_FIELDS`, без 5 toggle-ивентов;
  последовательные изменения Flow → корректные emissions.
- `AppSettingsDataStoreTest` (обновить): реальный DataStore поверх `PreferenceDataStoreFactory`
  с `backgroundScope` (существующий extension `newDataStore`): дефолт все true;
  `setFieldVisible(SETS, false)` → map `SETS = false`, остальные true.
- `EntryCreateViewModelTest` (обновить): stub `entryFieldVisibility`; при
  `{ SETS: false }` → `isSetsFieldVisible == false`, остальные true; значения полей
  не меняются.
- `EntryCreateUiStateTest` (обновить): `canSave` не зависит от скрытых опциональных полей.
- androidTest: `EntrySettingsScreenTest` (новый, Content без VM, паттерн
  AppearanceScreenTest): 5 тогглов + подзаголовок + TopAppBar; «Включить все»
  отсутствует/появляется; инверсия Switch через колбэк; `onEnableAll` ровно один вызов.
- androidTest: `EntryCreateScreenTest` (обновить): IconButton «Настроить экран» в
  TopAppBar + колбэк навигации; скрытое поле «Доп. вес» не рендерится; при видимом —
  прежнее поведение.

## Risks / Trade-offs

- [Combine требует стаба `entryFieldVisibility` в существующих JVM-тестах] →
  см. D3 «Внимание к тестам»; прогон `make test` до коммита.
- [`@StringRes` в domain — компромисс чистоты слоя] → аннотации стираются в рантайме;
  зафиксировано в D1, миграция в UI-extension механическая при требовании ревью.
- [Рост `RootScreen` → detekt LongMethod] → EntrySettingsNavDestination выносится
  отдельной функцией сразу.
- [Пользователь скрывает поле с невалидным «введённым до скрытия» значением —
  может не понять, почему запись сохранилась иначе] → значения опциональных полей
  не влияют на `canSave` (существующее правило), риск ограничен дефолтами; принято.

## Migration Plan

Без миграций: ключи новые, дефолт `true` читается при отсутствии; схема БД не меняется.
Откат — revert коммита, данные в `appearance_prefs` остаются безвредными.

## Open Questions

Нет.
