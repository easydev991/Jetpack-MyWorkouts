# Кастомные ProGuard/R8 правила.
# Compose, Room, DataStore и Navigation несут consumer-правила в самих AAR —
# дополнительные keep-правила для них не нужны.

# Читаемые стек-трейсы в проде (нужно и будущему Crashlytics).
-keepattributes SourceFile,LineNumberTable

# Crashlytics (подключим позже — правила уже на месте).
-keepattributes *Annotation*
-keepattributes exceptions
-keepattributes signature
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**
