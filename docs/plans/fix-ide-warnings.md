# План: исправление предупреждений IDE (lint + компилятор)

> **Статус: выполнен полностью** (сентябрь 2026).

## Контекст

Прогон `./gradlew compileGithubDebugKotlin --warning-mode all` + `./gradlew lintGithubDebug` выявил **23 проблемы**:

| Категория | Кол-во | Источник |
|---|---|---|
| Kotlin compiler deprecation | 4 | `Modifier.menuAnchor()` × 3, `fallbackToDestructiveMigration()` × 1 |
| Lint `UnusedResources` | 8 | мёртвые цвета в `colors.xml` + `ic_launcher_background` |
| Lint `ModifierParameter` | 4 | `modifier` стоит не первым опциональным параметром |
| Lint `RedundantLabel` | 1 | дублирующийся `android:label` в манифесте |
| Lint `ObsoleteSdkInt` | 1 | папка `mipmap-anydpi-v26` при `minSdk = 26` |
| Lint `AndroidGradlePluginVersion` | 1 | AGP `9.4.0` → есть `9.4.1` |
| Lint `GradleDependency` | 1 | compose-bom `2026.08.00` → есть `2026.09.00` |
| Lint hint `AutoboxingStateCreation` | 3 | `mutableStateOf(0)` вместо `mutableIntStateOf(0)` |

TDD не применяется — правки механические, без новой бизнес-логики.

## Стратегия

Шесть атомарных коммитов от безопасного к рискованному; стиль сообщений — русские фразы. После каждой фазы — зелёные `make format && make lint && make test`, для UI-фаз плюс `make android-test` (зелёные на протяжении всего плана, ниже не повторяются).

---

## Фаза 0 — Baseline

- [x] Стартовое состояние зафиксировано: дерево чистое, тесты зелёные, lint 16 warnings / 3 hints, компилятор — 4 deprecation

> Факт: счётчики baseline (753 JVM / 437 androidTest) оказались завышены накопленными до `clean` XML двух прогонов; свежие прогоны — 528 / 219+1 skip по тем же 27 классам, все зелёные.

---

## Фаза 1 — Безопасные удаления (коммит «Удаление неиспользуемых цветов и избыточных ресурсов»)

- [x] Удалены 7 мёртвых цветов из `colors.xml` и неиспользуемый `ic_launcher_background.xml` (иконка ссылается на `@drawable/`)
- [x] Убран дублирующий `android:label` у `<activity>` в `AndroidManifest.xml`
- [x] `mipmap-anydpi-v26/` слита в `mipmap-anydpi/` (minSdk = 26)

**Результат:** `RedundantLabel`, `ObsoleteSdkInt`, `UnusedResources` = 0; lint 6 warnings / 3 hints.

---

## Фаза 2 — `mutableIntStateOf` (коммит «Использование mutableIntStateOf для Int-состояний»)

- [x] `anchorHeight` ×3 (`ArchivedExercisesScreen`, `ExercisesScreen`, `MainScreen`) — `mutableStateOf(0)` → `mutableIntStateOf(0)` + импорт

**Результат:** `AutoboxingStateCreation` = 0; lint 6 warnings / 0 hints.

---

## Фаза 3 — Kotlin-deprecations (коммит «Обновление deprecation-вызовов в Room и Compose»)

- [x] `WorkoutDatabase.kt` — `fallbackToDestructiveMigration(dropAllTables = false)` (семантика прежнего no-arg overload в Room 2.8.5) + синхронный KDoc
- [x] `menuAnchor()` ×3 (`EntryCreateFields` ×2, `ExerciseCreateScreen`) — `.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)` + импорт. Тип подтверждён по исходникам material3 1.4.0: deprecated zero-arg ≡ `PrimaryNotEditable`, все три поля `readOnly = true`

**Результат:** 0 компиляторных deprecation; dropdown-тесты зелёные.

---

## Фаза 4 — `Modifier` первым опциональным (коммит «Перемещение modifier первым опциональным параметром»)

- [x] Все ~40 вызывающих проверены — именованные аргументы, reorder безопасен
- [x] `modifier` переставлен в 4 сигнатурах (`ExercisesScreen`, `ExercisesScreenContent`, `ExerciseListItemRow`, `MainScreen`)

**Результат:** `ModifierParameter` = 0.

---

## Фаза 5 — Зависимости (коммит «Обновление версии AGP»)

- [x] AGP `9.4.0` → `9.4.1`: clean-сборка и тесты зелёные, `AndroidGradlePluginVersion` = 0
- [x] Визуальный smoke пропущен осознанно — AGP-патч на рантайм UI не влияет

### 5.2 compose-bom `2026.08.00` → `2026.09.00` — НЕ выполняется

> Коммит «Откат compose BOM на 2026.08.00»: на 2026.09.00 стабильно падал github-androidTest — ui-test-junit4 v2 исполняет тесты в runTest, и в длинных наборах перестаёт исполняться viewModelScope-корутина сохранения (EntryEditArchivedExerciseTest). Бамп отложен «до проверки его тест-раннера». Следствие: осознанный `GradleDependency` (1 шт.) остаётся.

---

## Фаза 6 — Документация и финальная верификация

### 6.1 Документация (коммит «Обновление упоминаний fallbackToDestructiveMigration в актуальных документах»)

- [x] `AGENTS.md` + `openspec/specs/workout-data/spec.md` (×2) — имя API синхронизировано с кодом; `openspec/changes/archive/...` и тест-комментарий не тронуты (исторические записи)

### 6.2 Финальная верификация

- [x] Компилятор (clean, `--warning-mode all`): **0 варнингов**
- [x] Lint (github и rustore): 1 осознанный `GradleDependency`, 0 hints (было 16 / 3)
- [x] Тесты: JVM 528/528, androidTest 219 + 1 skip, 0 упавших; ktlint и detekt чистые

---

## Сводка по коммитам

| # | Категория | Коммит | Изменения | Риск |
|---|---|---|---|---|
| 1 | Удаление | Удаление неиспользуемых цветов и избыточных ресурсов | 6 файлов, +6/−12 | низкий |
| 2 | Оптимизация | Использование mutableIntStateOf для Int-состояний | 3 файла, +6/−3 | низкий |
| 3 | API-обновление | Обновление deprecation-вызовов в Room и Compose | 3 файла, +17/−5 | средний (API) |
| 4 | Рефакторинг | Перемещение modifier первым опциональным параметром | 2 файла, +8/−8 | средний (API) |
| 5 | Зависимости | Обновление версии AGP (compose-bom пропущен, см. 5.2) | 2 файла, +23/−20 | средний |
| 6 | Документация | Обновление упоминаний fallbackToDestructiveMigration в актуальных документах | 2 файла, +3/−3 | низкий |

**Итог:** 0 варнингов компилятора, 0 hints; lint — единственный осознанный `GradleDependency` (1) в flavor `github` и `rustore`.
