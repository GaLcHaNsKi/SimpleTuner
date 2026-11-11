# SimpleTuner

Автономный Android-тюнер для шестиструнной гитары и четырёхструнного баса.
Язык приложения — русский. Исходное ТЗ: [spec.md](spec.md).

## Текущий статус

Реализована первая версия тюнера: AudioRecord, определение высоты методом YIN,
фильтрация и выбор ближайшей струны, переключение гитары и баса, сохранение
режима, запрос разрешения, управление записью по жизненному циклу и экран Compose.
Математика и DSP проверены на синтетических сигналах.

Debug-версия установлена на телефон, пользователь сообщил об успешной проверке.
Формальная проверка на инструментах, задержка и потребление ресурсов ещё не
завершены; пороги сигнала и уверенности могут потребовать настройки.
Результаты проверок: [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Окружение

- Android Studio с поддержкой AGP 9.1.1; проект открывать из корневого каталога.
- JDK 17 или 21; JVM bytecode проекта — 17.
- Android SDK Platform 37 (стабильная), Build Tools 36.0.0, Platform Tools.
- Минимальная версия устройства: Android 8.0 / API 26.
- Gradle 9.3.1 поставляется через Wrapper, глобальная установка не нужна.
- Kotlin 2.3.21, Compose BOM 2026.09.00, Material 3; версии закреплены в
  [gradle/libs.versions.toml](gradle/libs.versions.toml).
- В `app` используется встроенная поддержка Kotlin AGP 9; плагин
  `org.jetbrains.kotlin.android` добавлять не нужно.

На новой машине задайте SDK через Android Studio либо создайте **локальный** файл:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

`local.properties` исключён из Git. Первое разрешение Gradle-зависимостей требует
интернета на машине разработчика; установленное приложение работает автономно.

В текущем окружении системная Java 21 — без `javac`. Для сборки выбирайте
установленный полный JDK 17:

```bash
export JAVA_HOME="$HOME/.jdks/jbr-17.0.14"
```

В Android Studio укажите этот же JDK в Settings → Build Tools → Gradle → Gradle JDK.

## Сборка и проверки

```bash
./gradlew :core:test
./gradlew :app:lintDebug :app:assembleDebug
```

Windows: вместо `./gradlew` используйте `gradlew.bat`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
JUnit-отчёт: `core/build/reports/tests/test/index.html`.
Lint-отчёт: `app/build/reports/lint-results-debug.html`.

Установка на подключённое устройство с разрешённой отладкой USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.simpletuner/.MainActivity
```

В проект добавлен CI для unit-тестов, lint и сборки debug APK; он запустится
после размещения репозитория на GitHub.

## Release APK

`./gradlew :app:lintRelease :app:assembleRelease` создаёт неподписанный
`app/build/outputs/apk/release/app-release-unsigned.apk`. Собранный вручную
релиз версии 0.1.0 дополнительно выровнен и подписан существующим ключом из
`~/IdeaProjects/LibraryAndroid/app/library-release.jks`; готовый файл —
`app/build/outputs/apk/release/app-release.apk`. Пароли и ключи в проект не
копировались, Gradle-конфигурация автоматической подписи не задана. Для следующих
обновлений потребуется сохранить тот же ключ и заново подписывать релиз.

Установленный debug APK подписан другим ключом, поэтому Android не обновит его
релизом поверх. Для установки релиза потребуется сначала удалить debug-версию;
при этом удалятся её локальные настройки.

## Порядок разработки

1. [Архитектура и решения](docs/ARCHITECTURE.md).
2. [План реализации с критериями приёмки](docs/DEVELOPMENT.md).
3. [Матрица автоматических и ручных проверок](docs/TESTING.md).

Следующий этап — испытать приложение на телефоне с гитарой и басом, измерить
задержку и расход ресурсов и уточнить пороги на реальном звуке.

Совместимость инструментов сверена по документации
[AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes),
[Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk) и
[Compose BOM](https://developer.android.com/develop/ui/compose/bom).
