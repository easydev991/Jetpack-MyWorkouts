# Design

## Context

См. proposal.md — Why. Запись хранит комментарий в `exercise_entries.comment`
(схема уже есть), главный экран его не получает: SQL-проекция
`ExerciseEntryDao.observeEntries` → `EntryListItemEntity` → `toListItem()` →
`EntryListItem` → `EntryListItemRow` не включает колонку. Точная цепочка уже
реализована для доп. веса в change `2026-09-05-entry-list-weight-display`
(заархивирован) — повторяем её шаг за шагом для нового поля.

## Goals / Non-Goals

**Goals:**

- Прокинуть `comment` по всей цепочке до UI с минимальным диффом.
- Вид строки без комментария — байт-в-байт прежний (гард).

**Non-Goals:**

- Изменение схемы БД и версии `WorkoutDatabase` — миграция не нужна.
- Изменения экрана создания записи (`draftOf` сохраняет как сохранял).
- `createdAt` в строке списка — по-прежнему не нужен.
- Метка слева у комментария (как «Дополнительный вес») — не требуется.

## Decisions

1. **Гард `!comment.isNullOrBlank()`, не `isNullOrEmpty()`.**
   `EntryCreateViewModel.draftOf` сохраняет комментарий как
   `state.comment.ifBlank { null }` БЕЗ trim — whitespace-only комментарий
   (например «\n  ») в БД возможен. `isNullOrEmpty()` показал бы пустую
   строку-призрак; `isNullOrBlank()` гарантирует отсутствие блока
   (spec-сценарий «Пустой комментарий не отображается»).

2. **`comment: String? = null` в `EntryListItem` — с дефолтом.**
   Около 28 call-site'ов (фиксатуры тестов и превью) должны
   компилироваться как раньше. Позиционных вызовов в кодовой базе нет —
   все call-site'ы именованные; дефолт плюс именованный стиль делают
   вставку между `weightKg` и `date` безопасной. Альтернатива (без
   дефолта) потребовала бы править все фиксатуры — отклонена.

3. **KDoc `EntryListItem` пересматривается.** Прежний текст утверждает
   «Без `comment`/`createdAt` — строке списка они не нужны (design
   Decision 2)» — решение устарело: comment теперь нужен, поэтому KDoc
   правится под новую реальность (про `createdAt` фраза сохраняется).

4. **UI-блок после блока веса, без отдельной Row.** В `EntryListItemRow`
   после `if (weightKg != null && weightKg > 0.0) { WeightRow(...) }`
   добавляется `if (!comment.isNullOrBlank()) { Text(...) }`. Параметры:
   `bodyMedium` + `onSurfaceVariant`, `maxLines = 2`,
   `overflow = TextOverflow.Ellipsis`. Расстояние — существующий
   `Arrangement.spacedBy(4.dp)` у Column, ничего добавлять не нужно.
   Альтернатива (объединять вес и комментарий в одну структуру) — лишняя
   вложенность, отклонена.

5. **Эллипсис не тестируется в UI-тесте.** Проверить обрезку текста через
   semantics невозможно (количество отрисованных строк не экспортируется в
   semantics). Состояние «длинный комментарий обрезается» покрывается
   превью (`EntryListItemRowPreview.kt`) — по конвенции проекта
   (`AGENTS.md`, Preview обязательны). UI-тест на обрезку не пишется.

## Risks / Trade-offs

- [Whitespace-only комментарий в БД] → гард `isNullOrBlank()` (Decision 1),
  тест на blank в UI-тестах.
- [JOIN-проекция шире — чуть больше данных в каждый лист] → колонка и так
  в строке таблицы, стоимость нулевая; серверности нет.
- [Дефолт `= null` маскирует забытый маппинг] → JVM-тест `MappersTest`
  проверяет прокидывание непустого значения, androidTest `ExerciseEntryDaoTest`
  — что проекция отдаёт комментарий.
