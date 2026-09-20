# Proposal

## Why

Экран создания записи (`EntryCreateScreen`) всегда показывает все 7 полей, даже если часть из них пользователю не нужна (подходы, доп. вес, дата, время, комментарий). Нужна возможность скрывать необязательные поля, не теряя их значений, — настройки видимости, управляемые enum-ом и хранящиеся в DataStore.

## What Changes

- Новый enum `EntryField` (SETS/WEIGHT/DATE/TIME/COMMENT) — 5 опциональных полей формы записи; добавление поля = одна строка в enum + пара строк локализации + флаг/условие рендера в форме по существующему паттерну (экран настроек и DataStore не меняются).
- Новый экран `EntrySettingsScreen` с тогглами видимости (Material3 Switch), отрисованными итерацией по `EntryField.entries`, кнопкой «Включить все» и TopAppBar как у `AppearanceScreen`.
- Кнопка входа: IconButton (`Icons.Filled.Settings`) в TopAppBar `EntryCreateScreen`, навигация на `Screen.EntrySettings`. Других точек входа (MoreScreen, BottomTabs, deep links) — нет.
- Настройки видимости — 5 булевых ключей в существующем `AppSettingsDataStore` (файл `appearance_prefs`), один комбинированный `Flow<Map<EntryField, Boolean>>` + один параметризованный `setFieldVisible(field, visible)`; ключи деривируются из enum.
- `EntryCreateScreen` рендерит опциональные поля только при включённом тоггле; значения скрытых полей НЕ сбрасываются (тоггл управляет только видимостью), `canSave` и логика несохранённых изменений не меняются.
- Аналитика: `UserActionType.TOGGLE_ENTRY_FIELD_VISIBILITY` (value вида `sets_off`), `UserActionType.ENABLE_ALL_ENTRY_FIELDS`, `AppScreen.ENTRY_SETTINGS` + маппинг в `screenForRoute`.
- Тесты: JVM (ViewModel, DataStore, UiState) и androidTest (Content-паттерн для нового экрана, регресс-тесты `EntryCreateScreen`); превью светлой/тёмной темы.

## Capabilities

### New Capabilities

- `entry-settings-screen`: экран настройки видимости опциональных полей формы записи — тогглы по `EntryField`, «Включить все», персист в DataStore, единственная точка входа из TopAppBar `EntryCreateScreen`, аналитика переключений.

### Modified Capabilities

- `workout-entry-screen`: отображение опциональных полей (подходы, доп. вес, дата, время, комментарий) подчиняется настройкам видимости; скрытие поля не влияет на его значение, `canSave` и подтверждение закрытия; в TopAppBar добавлена кнопка настройки экрана.
- `non-tab-screen-navigation`: в `Screen` добавлен `object EntrySettings : Screen(route = "entry_settings")`, зарегистрированный в NavHost `RootScreen`; не входит в `BottomTabs`; нижняя навигация скрыта на маршруте (паттерн Appearance).

## Impact

- Новые файлы: `domain/model/EntryField.kt`, `ui/screens/entry/EntrySettingsScreen.kt` (+Preview), `ui/viewmodel/EntrySettingsViewModel.kt`, `ui/state/EntrySettingsUiState.kt`, тесты и androidTest к ним.
- Изменяемые файлы: `data/preferences/AppSettingsDataStore.kt` (новые ключи + Flow/setter), `ui/state/EntryCreateUiState.kt` (5 флагов видимости), `ui/viewmodel/EntryCreateViewModel.kt` (combine с `entryFieldVisibility`), `ui/screens/entry/EntryCreateScreen.kt` (условия рендера + IconButton), `navigation/Screen.kt`, `ui/screens/root/RootScreen.kt` (composable, NavDestination-функция, screenForRoute), `analytics/AnalyticsEvent.kt`, `strings.xml` (ru/en).
- DataStore-схема: новые ключи в существующем `appearance_prefs` — без миграции (дефолт true читается при отсутствии ключа).
- Локализация: 12 новых ключей `entry_settings_*` / `entry_visibility_*` (title, subtitle, content_description, enable_all + 5 label + 3 hint) в `values/` (ru — дефолт) и `values-en/` (en).
