# Spec Delta

## MODIFIED Requirements

### Requirement: AppSettingsDataStore persists archive-dialog preference

`AppSettingsDataStore` SHALL expose `val dontShowArchiveDialog:
Flow<Boolean>` (default `false`) и suspend-метод
`setDontShowArchiveDialog(value: Boolean)` — чекбокс «Больше не
показывать» алерта архивации упражнения. Для алерта архивации категории —
ОТДЕЛЬНЫЙ флаг: `val dontShowCategoryArchiveDialog: Flow<Boolean>`
(default `false`) и suspend-метод
`setDontShowCategoryArchiveDialog(value: Boolean)` (ключ
`dont_show_category_archive_dialog`, НЕ общий с упражнениями
`dont_show_archive_dialog`). Оба ключа SHALL храниться в том же файле
`appearance_prefs` (БЕЗ отдельного `DataStore` и БЕЗ миграции —
Preferences DataStore расширяет схему автоматически при добавлении нового
ключа; существующие установки видят default `false` до явного opt-out
пользователя).

#### Scenario: dontShowArchiveDialog returns false on first launch

- **WHEN** `AppSettingsDataStore.dontShowArchiveDialog` is collected
  with no prior writes
- **THEN** the first emitted value is `false`

#### Scenario: setDontShowArchiveDialog persists and Flow re-emits

- **WHEN** `setDontShowArchiveDialog(true)` is awaited
- **THEN** subsequent `dontShowArchiveDialog` Flow collection emits
  `true`
- **WHEN** `setDontShowArchiveDialog(false)` is awaited
- **THEN** subsequent `dontShowArchiveDialog` Flow collection emits
  `false`

#### Scenario: dontShowCategoryArchiveDialog returns false on first launch

- **WHEN** `AppSettingsDataStore.dontShowCategoryArchiveDialog` is
  collected with no prior writes
- **THEN** the first emitted value is `false`

#### Scenario: setDontShowCategoryArchiveDialog persists and Flow re-emits

- **WHEN** `setDontShowCategoryArchiveDialog(true)` is awaited
- **THEN** subsequent `dontShowCategoryArchiveDialog` Flow collection
  emits `true`
- **WHEN** `setDontShowCategoryArchiveDialog(false)` is awaited
- **THEN** subsequent `dontShowCategoryArchiveDialog` Flow collection
  emits `false`
