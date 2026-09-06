## 1. Группировка записей по дате (TDD)

- [ ] 1.1 Написать JVM-тест `EntryDateGrouperTest` в
      `app/src/test/java/com/myworkouts/ui/screens/main/EntryDateGrouperTest.kt`
      (JUnit 5, без Android): группировка пустого списка, одной записи,
      двух записей одной даты, разных дат; граница полуночи в
      фиксированной зоне (запись за 23:59 вчера vs 00:01 сегодня
      попадает в разные секции — вчерашнюю и сегодняшнюю);
      неупорядоченный вход (`date` записей идёт в произвольном
      порядке) даёт секции и записи строго по убыванию `date`
      (см. design.md Decision 1 «Контракт группировки»); в
      результате нет ни одной `DateSection` с пустым `items`
      (контракт группировки: после фильтрации последней записи даты
      секция исчезает целиком; полный сценарий «long-press → алерт →
      удаление» покрывается androidTest §8.1).
      Запустить `make test` — убедиться, что тесты падают
      (компиляция/asserts), фиксируя контракт `groupEntriesByDate`
      и типа `DateSection`.
- [ ] 1.2 Реализовать `data class DateSection(val date: LocalDate,
      val items: List<EntryListItem>)` и чистую функцию
      `groupEntriesByDate(entries: List<EntryListItem>, today: LocalDate,
      zone: ZoneId): List<DateSection>` в новом файле
      `app/src/main/java/com/myworkouts/ui/screens/main/EntryDateGrouper.kt`.
      Реализация включает явную сортировку входа по `date` убыванием
      перед `groupBy` (см. design.md Decision 1 «Контракт группировки»).
      Запустить `make test` — убедиться, что `EntryDateGrouperTest`
      зелёный.

## 2. Форматтер заголовка секции (TDD)

- [ ] 2.1 Написать JVM-тест `SectionTitleFormatterTest` в
      `app/src/test/java/com/myworkouts/ui/screens/main/SectionTitleFormatterTest.kt`:
      «сегодня», «вчера», дата старше вчера (позавчера и более ранние)
      — формат `dd.MM.yyyy` с ведущими нулями; отдельного лейбла для
      позавчера нет, это ветка форматированной даты. Запустить
      `make test` — убедиться, что тесты падают, фиксируя контракт
      `sectionTitle`.
- [ ] 2.2 Реализовать `sectionTitle(date: LocalDate, today: LocalDate,
      todayLabel: String, yesterdayLabel: String,
      formatter: DateTimeFormatter): String` в новом файле
      `app/src/main/java/com/myworkouts/ui/screens/main/SectionTitleFormatter.kt`.
      Запустить `make test` — убедиться, что
      `SectionTitleFormatterTest` зелёный.

## 3. Локализованные строки

- [ ] 3.1 Добавить `entry_section_today` («Сегодня») в
      `app/src/main/res/values/strings.xml` и «Today» в
      `app/src/main/res/values-en/strings.xml`.
      Проверка: `make lint` зелёный, обе строки видны в ресурсном
      индексе.
- [ ] 3.2 Добавить `entry_section_yesterday` («Вчера») в
      `app/src/main/res/values/strings.xml` и «Yesterday» в
      `app/src/main/res/values-en/strings.xml`.
      Проверка: обе строки присутствуют в обоих файлах, `make lint`
      зелёный.

## 4. Расширение `MainUiState.Success`

- [ ] 4.1 Добавить поле `sections: List<DateSection> = emptyList()` в
      `MainUiState.Success` (сохранить `entries` для empty-check и
      `pendingDeleteId`-логики), импортировать `DateSection` из
      `com.myworkouts.ui.screens.main.EntryDateGrouper`. Дефолт
      `= emptyList()` обязателен — иначе сломаются все call site, где
      `MainUiState.Success(...)` создаётся без `sections`: ровно 10
      конструкций в трёх файлах —
      `MainScreenTest.kt:93,129,170,320,345,385,466`,
      `MainScreenPreview.kt:82,100`, `MainViewModel.kt:56`. Запустить
      `./gradlew compileDebugKotlin` и `./gradlew
      compileDebugAndroidTestKotlin` — убедиться, что прод и оба типа
      тестов компилируются.

## 5. Производное `sections` в `MainViewModel`

- [ ] 5.1 Расширить конструктор `MainViewModel` параметрами
      `clock: () -> LocalDate = LocalDate::now` и
      `zone: ZoneId = ZoneId.systemDefault()` (значения по умолчанию
      — без них не сломаются существующие call site и unit/androidTest).
      Дефолты обязательны: существующий `MainViewModelTest.kt:118`
      создаёт `MainViewModel(repository)` напрямую, и три существующих
      вызова `MainViewModel.factory(repository)`
      (`RootScreen.kt:86`, `MainScreenEditNavigationTest.kt:125`,
      `MainScreenFabNavigationTest.kt:88`) тоже должны продолжать
      компилироваться без правок — `factory` остаётся с сигнатурой
      `factory(repository)` именно благодаря дефолтам в конструкторе
      `MainViewModel` (factory их не принимает и не пробрасывает).
      Проверка: `make test` и
      `./gradlew compileDebugAndroidTestKotlin` зелёные.
- [ ] 5.2 В блоке
      `combine(pendingDeleteId, entries) { pendingId, entries -> ... }`
      вычислить `val sections = groupEntriesByDate(entries, clock(), zone)`
      и собрать `MainUiState.Success(entries, pendingId, sections)`.
      `groupEntriesByDate(...)` — обычная pure-функция, а не `Flow`,
      поэтому она вызывается внутри transform-блока `combine`, а не
      как его аргумент. Запустить `./gradlew compileDebugKotlin` —
      убедиться, что прод собирается.

## 6. Рендер секций в `MainScreen`

- [ ] 6.0 Создать
      `@Composable internal fun SectionHeader(title: String, modifier: Modifier = Modifier)`
      в новом файле
      `app/src/main/java/com/myworkouts/ui/screens/main/SectionHeader.kt`
      (по тому же паттерну, что и `EntryListItemRow.kt`): `Text` с
      `MaterialTheme.typography.titleMedium` и фоном
      `Modifier.background(colorScheme.surface)` — без фона залипающий
      `stickyHeader` просвечивал бы строками списка при скролле;
      отдельных отступов нет. Локализованные строки
      `entry_section_today` / `entry_section_yesterday` и
      `DateTimeFormatter.ofPattern("dd.MM.yyyy")`
      резолвятся на уровне вызывающего composable (как `separator`,
      который резолвится в `SuccessBody` и пробрасывается через
      `EntryList` дальше), `SectionHeader` принимает уже готовую
      строку.
- [ ] 6.1 Заменить в `EntryListItems` плоский `items(items, key = …)` на
      цикл
      `sections.forEach { section -> stickyHeader(key = section.date.toEpochDay(),
      contentType = "section") { SectionHeader(…) }
      + items(section.items, key = { it.id }, contentType = { "row" }) }`
      (`stickyHeader` принимает `key`/`contentType` как значения, не
      лямбды — в отличие от `items`, где те же параметры — лямбды).
      Локализованные строки
      `entry_section_today` / `entry_section_yesterday` и
      `DateTimeFormatter.ofPattern("dd.MM.yyyy")` прокидываются
      параметрами в `SectionHeader`. Запустить
      `./gradlew compileDebugKotlin` и `make lint` — убедиться,
      что нет ошибок компиляции и предупреждений detekt.
- [ ] 6.2 Поменять сигнатуру `EntryList(items, …)` →
      `EntryList(sections: List<DateSection>, …)` (private fun в
      `MainScreen.kt:254`) и `EntryListItems(items, …)` →
      `EntryListItems(sections: List<DateSection>, …)` (private fun в
      `MainScreen.kt:309`); `success.entries` в `SuccessBody`
      (`MainScreen.kt:181-226`) остаётся для empty-check (строка 186)
      и `pendingItem`-lookup (строка 217). Тело списка получает
      `List<DateSection>` вместо `List<EntryListItem>`.

## 7. Превью

- [ ] 7.1 Добавить в `MainScreenPreview.kt` рядом с существующим
      `MainScreenContentListPreview` (не вместо него) сценарий с тремя
      секциями (сегодня / вчера / дата `dd.MM.yyyy`) — два
      превью-метода с тем же двух-аннотационным паттерном
      (`@Preview(...)` + `@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, ...)`),
      `showBackground = true`, `locale = "ru"`, обёртка в
      `JetpackMyWorkoutsTheme`, ключевые состояния рядом в `Column`.
      Чтобы превью получило корректные `sections` без `MainViewModel`:
      `entries` берутся с датами `LocalDate.now()`,
      `LocalDate.now().minusDays(1)` и `LocalDate.now().minusDays(5)`
      (фиксированная `dd.MM.yyyy` дата), а
      `sections = groupEntriesByDate(entries, LocalDate.now(),
      ZoneId.systemDefault())` считается явно при сборке
      `MainUiState.Success(...)` для превью. Запустить
      `make build` — убедиться, что превью компилируется.

## 8. Регрессионные androidTest

- [ ] 8.1 Написать androidTest в
      `app/src/androidTest/java/com/myworkouts/ui/screens/main/MainScreenSectionsTest.kt`:
      длинное нажатие работает в секции (long-press на строке открывает
      контекстное меню с «Изменить»/«Удалить»); удаление работает в
      секции (выбор «Удалить» + подтверждение в алерте убирает запись);
      sticky-header виден (заголовок секции остаётся у верхнего края
      `LazyColumn` при скролле); заголовок «Сегодня» в ru-локали и
      «Yesterday» в en-локали. Использовать `createAndroidComposeRule`,
      `MainViewModel(repository, clock = ..., zone = ...)` напрямую
      с фиксированными `clock`/`zone` (по образцу существующего
      `MainViewModelTest.kt:118` — отдельный `TestMainViewModel` не
      нужен, см. design.md Decision 3), фейковый репозиторий на
      `MutableStateFlow`. Локаль в тесте переключается через
      `LocaleListCompat.forLanguageTags("en")` +
      `context.createConfigurationContext(Configuration(...).apply { setLocale(it) })` +
      `CompositionLocalProvider(LocalContext provides localizedContext) {}`
      — стандартный идиом для androidTest без `AppCompatDelegate`
      (зависимость `androidx.appcompat` в проекте не подключена).
      Альтернатива через отдельные тестовые конфигурации
      `res/values-*/strings.xml` признана избыточной — две параллельные
      иерархии ресурсов ради двух строк-заголовков. Запустить
      `make android-test` — убедиться, что тесты зелёные.

## 9. Финальная проверка

- [ ] 9.1 Прогнать `make format && make lint && make test &&
      make android-test` — все четыре цели зелёные. Если падают —
      починить до прохождения. Изменение готово к архивации.
