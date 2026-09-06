## 1. Группировка записей по дате (TDD)

- [x] 1.1 Написать JVM-тест `EntryDateGrouperTest` в
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
- [x] 1.2 Реализовать `data class DateSection(val date: LocalDate,
      val items: List<EntryListItem>)` и чистую функцию
      `groupEntriesByDate(entries: List<EntryListItem>,
      zone: ZoneId = ZoneId.systemDefault()): List<DateSection>` в
      новом файле
      `app/src/main/java/com/myworkouts/ui/screens/main/EntryDateGrouper.kt`.
      Реализация включает явную сортировку входа по `date` убыванием
      перед `groupBy` (см. design.md Decision 1 «Контракт группировки»).
      Запустить `make test` — убедиться, что `EntryDateGrouperTest`
      зелёный.

## 2. Заголовок секции — инлайн на рендере

- [x] 2.1 В `EntryListItems` (`MainScreen.kt`) резолвить заголовок
      секции инлайном: `if (section.date == yesterday) yesterdayLabel
      else section.date.format(formatter)`, где `yesterdayLabel` —
      `stringResource(R.string.entry_section_yesterday)`, `formatter` —
      `remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }`,
      `today`/`yesterday` — `remember { LocalDate.now() }`.
      Отдельную функцию/файл не заводить: выражение из одной строки,
      единственный call-site, тестировать нечего — форматтер из stdlib
      (design Decision 4). Секция «Сегодня» заголовка не имеет —
      фильтруется до этого выражения (design Decision 5).
      Проверка: `make lint` зелёный, локали покрываются androidTest §8.1.

## 3. Локализованные строки

- [x] 3.1 Добавить `entry_section_yesterday` («Вчера») в
      `app/src/main/res/values/strings.xml` и «Yesterday» в
      `app/src/main/res/values-en/strings.xml`. Строку для «Сегодня»
      не добавлять: секция «Сегодня» отображается без заголовка
      (design Decision 5).
      Проверка: обе локали присутствуют, `make lint` зелёный.

## 4. `MainUiState.Success` без секций

- [x] 4.1 `MainUiState.Success` остаётся парой
      `(entries, pendingDeleteId)` — поле `sections` НЕ добавлять:
      секции — производное от `entries`, рендерер считает их сам через
      `remember` (design Decision 1/2), расхождение невозможно по
      построению. Проверка: `make test` и
      `./gradlew compileDebugAndroidTestKotlin` зелёные — существующие
      конструкторы `Success(...)` в тестах и превью не меняются.

## 5. Группировка на рендере

- [x] 5.1 В `SuccessBody` (`MainScreen.kt`) вычислить секции перед
      `EntryList`: `val sections = remember(success.entries)
      { groupEntriesByDate(success.entries) }` — зона по дефолту
      системная, `MainViewModel` не меняется (ни параметров, ни
      `combine`-группировки; design Decision 1/3). `remember` кэширует
      результат до смены `entries` — стоимость та же, что у расчёта
      на эмиссию. Запустить `./gradlew compileDebugKotlin` —
      убедиться, что прод собирается.

## 6. Рендер секций в `MainScreen`

- [x] 6.0 Создать
      `@Composable internal fun SectionHeader(title: String, modifier: Modifier = Modifier)`
      в новом файле
      `app/src/main/java/com/myworkouts/ui/screens/main/SectionHeader.kt`
      (по тому же паттерну, что и `EntryListItemRow.kt`): `Text` со
      стилем по умолчанию (16sp, обычный вес), цветом
      `colorScheme.onSurfaceVariant`, по бокам линии того же цвета
      (1dp, зазор 12dp, отступ 16dp), вертикальным отступом 8dp и
      фоном
      `Modifier.background(colorScheme.surface)` — без фона залипающий
      `stickyHeader` просвечивал бы строками списка при скролле.
      Локализованная строка
      `entry_section_yesterday` и
      `DateTimeFormatter.ofPattern("dd.MM.yyyy")`
      резолвятся на уровне вызывающего composable (как `separator`,
      который резолвится в `SuccessBody` и пробрасывается через
      `EntryList` дальше), `SectionHeader` принимает уже готовую
      строку.
- [x] 6.1 Заменить в `EntryListItems` плоский `items(items, key = …)` на
      цикл
      `sections.forEach { section -> if (section.date != today)
      stickyHeader(key = section.date.toEpochDay(),
      contentType = "section") { SectionHeader(…) }
      + items(section.items, key = { it.id }, contentType = { "row" }) }`
      — секция «Сегодня» рендерится без заголовка (design Decision 5)
      (`stickyHeader` принимает `key`/`contentType` как значения, не
      лямбды — в отличие от `items`, где те же параметры — лямбды).
      Заголовок секции собирается на call-site инлайном (§2.1).
      Запустить
      `./gradlew compileDebugKotlin` и `make lint` — убедиться,
      что нет ошибок компиляции и предупреждений detekt.
- [x] 6.2 Поменять сигнатуру `EntryList(items, …)` →
      `EntryList(sections: List<DateSection>, …)` (private fun в
      `MainScreen.kt:254`) и `EntryListItems(items, …)` →
      `EntryListItems(sections: List<DateSection>, …)` (private fun в
      `MainScreen.kt:309`); `success.entries` в `SuccessBody`
      (`MainScreen.kt:181-226`) остаётся для empty-check (строка 186)
      и `pendingItem`-lookup (строка 217). Тело списка получает
      `List<DateSection>` вместо `List<EntryListItem>`.

## 7. Превью

- [x] 7.1 Добавить в `MainScreenPreview.kt` рядом с существующим
      `MainScreenContentListPreview` (не вместо него) сценарий с тремя
      секциями (сегодня / вчера / дата `dd.MM.yyyy`) — два
      превью-метода с тем же двух-аннотационным паттерном
      (`@Preview(...)` + `@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, ...)`),
      `showBackground = true`, `locale = "ru"`, обёртка в
      `JetpackMyWorkoutsTheme`, ключевые состояния рядом в `Column`.
      Превью подаёт только `entries`: даты `LocalDate.now()`,
      `LocalDate.now().minusDays(1)` и `LocalDate.now().minusDays(5)`
      (фиксированная `dd.MM.yyyy` дата) — секции рендерер считает сам
      (design Decision 1). Запустить
      `make build` — убедиться, что превью компилируется.

## 8. Регрессионные androidTest

- [x] 8.1 Написать androidTest в
      `app/src/androidTest/java/com/myworkouts/ui/screens/main/MainScreenSectionsTest.kt`:
      длинное нажатие работает в секции (long-press на строке открывает
      контекстное меню с «Изменить»/«Удалить»); удаление работает в
      секции (выбор «Удалить» + подтверждение в алерте убирает запись);
      sticky-header виден (заголовок секции остаётся у верхнего края
      `LazyColumn` при скролле); секция «Сегодня» — без заголовка
      (записи сразу вверху, текста «Сегодня»/«Today» нет), заголовок
      «Вчера» в ru-локали и
      «Yesterday» в en-локали. Использовать `createAndroidComposeRule`,
      `MainViewModel(repository)` напрямую (по образцу существующего
      `MainViewModelTest.kt:118` — отдельный `TestMainViewModel` не
      нужен), фейковый репозиторий на
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

- [x] 9.1 Прогнать `make format && make lint && make test &&
      make android-test` — все четыре цели зелёные. Если падают —
      починить до прохождения. Изменение готово к архивации.
