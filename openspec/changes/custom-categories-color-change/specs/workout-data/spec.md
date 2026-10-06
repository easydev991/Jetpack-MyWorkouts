# Spec Delta

## ADDED Requirements

### Requirement: Мутации категорий в API репозитория — создание и обновление

Интерфейс `WorkoutDataRepository` SHALL предоставлять `addCategory(name:
String, colorArgb: Int): Result<Category>` — цвет приходит готовым из
формы категории, без внутренней генерации (контракт — спека
`category-data`, требование «Создание категории с цветом из формы») — и
`updateCategory(id: Long, newName: String, colorArgb: Int):
Result<Unit>` — обновление имени и цвета одной транзакцией с каскадом
ссылок упражнений по имени и guard стандартной категории
(`StandardCategoryDeletionException`; контракт — спека `category-data`,
требование «Обновление категории — имя и цвет одной транзакцией»).
Метод `renameCategory` SHALL быть удалён из кода интерфейса
`WorkoutDataRepository` (декларация об интерфейсе кода — в основной
спеке workout-data категорийных мутаций нет, из спеки удалять нечего):
его поведение полностью покрывается `updateCategory`.

#### Scenario: addCategory персистит переданный цвет

- **WHEN** вызывается `addCategory("Плечи", colorArgb)`
- **THEN** созданная категория имеет переданный `colorArgb`
- **AND** репозиторий не генерирует цвет внутри операции

#### Scenario: updateCategory заменяет renameCategory

- **WHEN** экран категории сохраняет редактирование
- **THEN** вызывается `updateCategory(id, newName, colorArgb)` — одна
  транзакция обновляет имя и цвет, каскад ссылок упражнений по имени
  сохранён, стандартная категория отклоняется
  (`StandardCategoryDeletionException`)
