# Кастомные ProGuard/R8 правила.
# Compose, Room, DataStore и Navigation несут consumer-правила в самих AAR —
# дополнительные keep-правила для них не нужны. Firebase (Crashlytics/Analytics)
# тоже: consumer-правила в AAR покрывают всё необходимое, ручной
# broad-keep `-keep class com.google.firebase.crashlytics.** { *; }` удалён
# как избыточный (release-сборка и smoke-старт без него зелёные).

# Читаемые стек-трейсы в Crashlytics — пара из доков Firebase:
# атрибуты имён/строк сохраняются, оригинальные имена файлов в трейсах
# скрыты константой SourceFile; деобфускация — через загруженный mapping
# (uploadCrashlyticsMappingFile* на release-сборке).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Crashlytics/Firebase: аннотации, исключения и generic-сигнатуры
# (нужны SDK для рефлексии).
-keepattributes *Annotation*
-keepattributes exceptions
-keepattributes signature
