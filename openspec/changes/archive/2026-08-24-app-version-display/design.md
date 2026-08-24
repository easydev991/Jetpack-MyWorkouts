## Context

Эталонный проект `JetpackDays` показывает версию приложения внизу экрана
`MoreScreen` через `BuildConfig.VERSION_NAME`. В `Jetpack-MyWorkouts` сейчас
версия в `app/build.gradle.kts` захардкожена (`versionCode = 1`,
`versionName = "1.0"`), `gradle.properties` не содержит `VERSION_NAME`/
`VERSION_CODE`, а `buildFeatures.buildConfig` не включён — значит, класс
`com.myworkouts.BuildConfig` не генерируется и импорт не скомпилируется.

Тело `Scaffold` в `MoreScreen.kt` пустое (`Scaffold` имеет только
`topBar = { TopAppBar(...) }`), строки `app_version` нет ни в
`res/values/strings.xml`, ни в `res/values-en/strings.xml`. `dimens.xml`
в проекте ещё не вводился.

Цель — перенести паттерн JetpackDays в текущий проект минимальным
диффом: Gradle-конфигурация + ресурсы + одно `Text` в `MoreScreen`.
Без новых зависимостей, без изменений в domain/data слоях, без сетевых
зависимостей (проект offline-only).

## Goals / Non-Goals

**Goals:**

- Хранить `VERSION_NAME`/`VERSION_CODE` в `gradle.properties` и читать их
  в `app/build.gradle.kts` тем же `project.findProperty(...)?.toString()?.toInt()`
  паттерном, что и JetpackDays.
- Включить `buildFeatures.buildConfig = true` в `app/build.gradle.kts`,
  чтобы `BuildConfig.VERSION_NAME` стал доступен в `app/src/main/java/`.
- Добавить локализованную строку `app_version` (`%1$s`) в ru/en.
- Показывать версию внизу `MoreScreen` через `BuildConfig.VERSION_NAME`
  в стиле JetpackDays: `bodyMedium`, `onSurface.copy(alpha = 0.6f)`,
  `TextAlign.Center`, `fillMaxWidth`.
- Синхронизировать `openspec/specs/more-screen/spec.md` с новым требованием.
- `make format && make lint && make test` — зелёные.

**Non-Goals:**

- `dimens.xml` — не вводим (на экране всего два повтора `8.dp`, ниже
  порога, см. Решение 3).
- Кнопки действий (отзыв/оценка/поделиться) на экране «Ещё» — отдельная
  фича.
- `androidTest` для `MoreScreen` — избыточно для одной статичной строки;
  значение читается из `BuildConfig` (генерируется Gradle), тестировать
  нечего.
- UI-снапшоты / Preview — избегаем преждевременного визуального
  закрепления.
- Изменения в `NavigationBar`/`RootScreen` — версия отображается только
  на экране «Ещё».
- Локализация числового `versionCode` — пользователю показывается только
  `versionName` (`BuildConfig.VERSION_NAME`).

## Decisions

### 1. Формат `VERSION_NAME` — SemVer `1.0.0`

JetpackDays использует `1.13.2` (Major.Minor.Patch). В `Jetpack-MyWorkouts`
стартуем с `1.0.0`, `VERSION_CODE=1` — единая схема с эталоном, явный
patch-номер сразу. Альтернатива `1.0` (текущее значение) отвергнута: не
даёт patch-уровня и расходится с паттерном JetpackDays.

### 2. `buildFeatures.buildConfig = true` обязательно

В AGP 8+ `BuildConfig` отключён по умолчанию. Без явного
`buildConfig = true` импорт `com.myworkouts.BuildConfig` падает на
компиляции. Включение флага — единственный способ получить класс
`BuildConfig.VERSION_NAME` без дополнительных Gradle-плагинов.

### 3. Хардкод `8.dp` вместо `dimens.xml`

На экране только два повтора `8.dp` (внешний `padding` на `Box` и
`padding` на `Text`). По соглашению команды `dimens.xml` вводится,
когда значение повторяется **≥ 3 раз** или выходит за пределы одного
экрана. Два повтора в одном файле — ниже порога. Альтернатива
(`dimens.xml` с `spacing_xs`) отвергнута как преждевременная.

### 4. `Box` с `Alignment.BottomCenter` для прижатия к низу

Минимальная достаточная структура для одного `Text`:
`Box(Modifier.fillMaxSize().padding(paddingValues).padding(8.dp))` с
`Text(... Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp) ...)`.

Альтернативы рассмотрены и отвергнуты:

- `Column { Spacer(Modifier.weight(1f)); Text(...) }` — избыточно:
  единственный ребёнок не нуждается в `weight` для прижатия, лишний
  вложенный композабл.
- `Modifier.padding(bottom = 56.dp)` на `Text` — хрупкое: при смене
  высоты `NavigationBar` ломается, не масштабируется.
- `verticalScroll` — нечего скроллить: одна строка статичного текста.

### 5. `openspec/specs/more-screen/spec.md` — канонический spec правится напрямую на Этапе 4

В стандартном OpenSpec-флоу `spec.md` обновляется через
proposal → tasks → implementation → archive → sync specs (отдельный
`proposal → archive` цикл, который переносит delta из
`openspec/changes/<name>/specs/` в канонический
`openspec/specs/`). Здесь правка локальная (одно требование, без
архитектурных решений), поэтому в этом change-цикле пропускаем шаг
`archive → sync-specs` и правим канонический
`openspec/specs/more-screen/spec.md` напрямую на Этапе 4; delta-файл
в `openspec/changes/app-version-display/specs/more-screen/spec.md`
сохраняется как стандартная запись изменений в рамках текущего change.

Решение зафиксировано в самой задаче (`docs/app-version-display.md`,
раздел «Отступление от OpenSpec-флоу»). Альтернатива — полный
proposal-change цикл с архивацией — отвергнута как избыточная для
правки одного требования.

### 6. Локализация через `stringResource(R.string.app_version, ...)`

Стандартный Android-механизм `stringResource` с плейсхолдером `%1$s`.
Никаких кастомных утилит форматирования — встроенный `Context.getString`
сам подставляет аргументы и обрабатывает локаль. Альтернатива
(хранение шаблона в `BuildConfig`/`constants`) отвергнута: нарушает
единый источник правды для строк UI.

## Risks / Trade-offs

- **`buildFeatures.buildConfig = true` добавляет пустой `BuildConfig.java`
  в скомпилированный APK** → размер артефакта вырастает на ~1 КБ.
  Митигация: приемлемо для офлайн-приложения; при необходимости —
  R8 минификация уберёт неиспользуемые поля автоматически.

- **Строка `app_version` дублируется в ru/en** → при добавлении новых
  языков нужно не забыть перевод. Митигация: документировано в
  требованиях (`openspec/specs/more-screen/spec.md`, новый
  `Requirement: MoreScreen version text shows BuildConfig.VERSION_NAME`),
  переводчик/разработчик увидит отсутствующую локаль по провалу билда.

- **`versionName = "1.0"` (без patch) уже в `app/build.gradle.kts`** →
  при первом релизе придётся поднять до `1.0.0` явно. Митигация:
  правится в Этапе 1 одновременно с добавлением property, чтобы не
  было промежуточного состояния.

- **Версия отображается только на экране «Ещё»** → на других экранах
  пользователь её не видит. Митигация: достаточно для MVP, при
  необходимости — добавить в `AboutScreen` отдельной фичей.

- **`Box` без `verticalScroll`** → если в будущем в тело `Scaffold`
  добавятся другие элементы (список, кнопки), `Box` придётся заменить
  на `Column` или `LazyColumn`. Митигация: в Non-Goals явно зафиксировано,
  что другие действия — отдельная фича; когда она появится —
  соответствующий рефакторинг.

## Migration Plan

- Изменения только в `app/src/main/` (Gradle, ресурсы, UI) и
  `openspec/specs/more-screen/spec.md` — никаких миграций данных,
  схем, БД, сетевых контрактов.
- Развёртывание: стандартный `make install` (или прямой
  `./gradlew installDebug`). Никаких feature-flags или поэтапного
  rollout — приложение офлайн, без серверной части.
- Откат: `git revert <commit>` (один коммит) — все изменения
  изолированы в `app/build.gradle.kts`, `gradle.properties`,
  `res/values{,-en}/strings.xml`, `MoreScreen.kt`, `spec.md`.

## Open Questions

None at design time — все технические решения однозначно зафиксированы
в `docs/app-version-display.md` (раздел «Решения»), противоречий между
proposal / design / delta-spec / tasks не выявлено.
