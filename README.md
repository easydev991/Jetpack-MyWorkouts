# Мои тренировки

<!-- BEGIN_VERSIONS -->
[<img alt="Kotlin Version" src="https://img.shields.io/badge/Kotlin_Version-2.4.20-purple">](https://kotlinlang.org/)
[<img alt="Android SDK" src="https://img.shields.io/badge/Android_SDK-37-green">](https://developer.android.com/)
[<img alt="Min SDK" src="https://img.shields.io/badge/Min_SDK-26-informational">](https://developer.android.com/)
[<img alt="Gradle" src="https://img.shields.io/badge/Gradle-9.8.0-blue">](https://gradle.org/)
[<img alt="AGP" src="https://img.shields.io/badge/AGP-9.4.1-green">](https://developer.android.com/tools/releases/gradle-plugin)
<!-- END_VERSIONS -->

- Идея приложения в том, чтобы записывать свои тренировки — упражнение, подходы, повторения, доп.вес, длительность и комментарий, — а потом наглядно отслеживать прогресс: статистику, рекорды и активность
- Приложение работает полностью офлайн — все данные хранятся локально на устройстве
- Приложение автоматически адаптирует размер шрифтов к системным настройкам

## Что можно делать в приложении

1. Создавать/изменять/удалять записи о тренировках: упражнение, подходы, повторения, вес, длительность, комментарий
2. Управлять списком упражнений: стандартные и свои, с категориями и иконками, архивировать неиспользуемые, менять/удалять свои упражнения
3. Смотреть статистику за неделю/месяц/год: рекорды за подход, серии тренировок, распределение по категориям
4. Отслеживать активность на тепловой карте

## Начало работы

1. Клонируйте репозиторий
2. В терминале перейдите в папку с проектом
3. Все команды доступны через `Makefile`:

- Ознакомиться с доступными командами можно, выполнив команду:

```shell
make help
```

## Скриншоты

| Список тренировок | Создание записи | Новая запись | Статистика за неделю | Рекорды | Активность |
| --- | --- | --- | --- | --- | --- |
| <img alt="" src="./fastlane/metadata/android/ru-RU/images/phoneScreenshots/1-workouts-list.png"> | <img alt="" src="./fastlane/metadata/android/ru-RU/images/phoneScreenshots/2-create-new-entry.png"> | <img alt="" src="./fastlane/metadata/android/ru-RU/images/phoneScreenshots/3-new-entry-saved.png"> | <img alt="" src="./fastlane/metadata/android/ru-RU/images/phoneScreenshots/4-week-stats.png"> | <img alt="" src="./fastlane/metadata/android/ru-RU/images/phoneScreenshots/5-records-per-set.png"> | <img alt="" src="./fastlane/metadata/android/ru-RU/images/phoneScreenshots/6-activity-screen.png"> |

### Релизный процесс

Проект собирается в двух `productFlavors` — `rustore` (RuStore, основной канал) и `github` (GitHub Release, альтернативный). Команды `make rustore` (RuStore — все шаги одной командой, включая загрузку в RuStore по API) или `make rustore-draft` → `make rustore-commit VID=<vid>` (черновик + ручная отправка на модерацию); `make apk FLAVOR=github` для APK.

Инструкция по созданию сборки и управлению версиями: [deployment.md](docs/deployment.md)
