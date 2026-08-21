# ZeroPhone

Домашний лаунчер (home launcher) с белым списком приложений и блокировкой всего остального через Device Owner.

## О проекте

ZeroPhone — Android-приложение (Kotlin + Jetpack Compose), которое выступает домашним экраном устройства и позволяет ограничить доступ к приложениям:

- **Home Launcher.** `MainActivity` объявлена с `CATEGORY_HOME` + `CATEGORY_DEFAULT` (и `MAIN/LAUNCHER`), при нажатии кнопки «Домой» система предлагает выбрать ZeroPhone домашним экраном. На экране отображается сетка launchable-приложений **только из allowlist**.
- **Allowlist.** Экран управления белым списком: список всех launchable-приложений с чекбоксами (разрешить/заблокировать) и поиском. Изменения применяются немедленно.
- **Блокировка.** Все установленные launchable-приложения вне allowlist блокируются через `DevicePolicyManager.setPackagesSuspended(packages, true)`. Политика применяется при старте, при изменении allowlist, по `BOOT_COMPLETED` и после окончания emergency-окна. Никогда не блокируются: сам ZeroPhone (`com.numenlabs.zerophone`), системные критические пакеты (`com.android.phone`, `com.android.dialer`, `com.android.settings`, `com.android.systemui`, `com.android.packageinstaller`/`installer` и др.), активный IME, лаунчер по умолчанию, а также пакеты, чья блокировка бросает исключение.
- **Emergency Unlock.** Кнопка «Экстренная разблокировка на 30 минут»: снимает блокировку со всех приложений, показывает обратный отсчёт и автоматически повторно блокирует (re-lock) через 30 минут (AlarmManager, `RTC_WAKEUP`). Окно переживает перезагрузку: `BOOT_COMPLETED`-receiver переприменяет политику и пере-планирует re-lock, если окно активно (deadline хранится в persistence). Длительность окна настраивается (пресеты 5/15/30/60 мин или своя).
- **Контекстный движок.** Каждая возможность (пакет или логическая capability) резолвится движком ровно в одно из пяти состояний: `AVAILABLE` / `RESTRICTED` / `TEMPORARILY_AVAILABLE` / `CONTEXTUAL` / `BLOCKED`. Решения переводятся в `setPackagesSuspended` только для `BLOCKED`; защищённые `SuspendPolicy` пакеты никогда не блокируются. Правила конфликтуют детерминированно (специфичность → приоритет → id); гранты («временно доступна») истекают автоматически с re-lock через AlarmManager; ограниченные возможности получают дневной бюджет времени.
- **Режимы.** Именованные профили WORK / REST / FOCUS с сидами правил (seed только ограничивает логические capabilities и никогда не расширяет пакетывые дефолты); переключение режима с главного экрана мгновенно пере-применяет политику.
- **Источники данных.** Календарь — `CalendarProvider` (runtime-разрешение `READ_CALENDAR`; без него — пустое состояние); задачи/напоминания — собственный локальный стор (DataStore); важные непрочитанные — `NotificationListenerService` с чистым Kotlin-фильтром важности (пользовательский список приоритетных пакетов + важность канала ≥ HIGH).

### Условия работы

- Для реальной блокировки приложение должно быть назначено **Device Owner** (см. Provisioning ниже).
- **Без Device Owner** приложение корректно работает как обычный лаунчер: показывает сетку allowlist-приложений и запускает их, но **не блокирует** ничего — в UI отображается предупреждение о том, что Device Owner не назначен.
- Блокировка обратима: MVP не использует `wipeData()`, `lockNow()` и другие необратимые действия; системные пакеты не блокируются.

## Технические требования

- Gradle-мульти-модуль (монорепа, растёт по фазам):
  - `:core:model` — чистый Kotlin без Android-зависимостей: доменные типы (emergency-окна, задачи, события календаря);
  - `:core:policy` — политика блокировки: `PolicyApplier`, `SuspendPolicy`, re-lock scheduling, Android-адаптеры `DevicePolicyManager`/`AlarmManager`;
  - `:core:context` — контекстный движок (пять состояний доступности, правила, гранты, бюджеты, каталог режимов WORK/REST/FOCUS);
  - `:core:data` — источники данных: календарь (`CalendarProvider`), локальный стор задач (DataStore), важные непрочитанные уведомления (`NotificationListenerService` + чистый фильтр важности), Android-реализация `SnapshotProvider`;
  - `:core:ui` — Compose-тема и общие UI-зависимости;
  - `:feature:home`, `:feature:allowlist` — экраны;
  - `:app` — launcher-activity (`CATEGORY_HOME`), ресиверы `BOOT_COMPLETED`/AlarmManager, сборка APK. `applicationId` / `namespace` — `com.numenlabs.zerophone`.
- Kotlin + Jetpack Compose (Compose BOM 2026.02.01), AGP 9.2.1.
- `minSdk 24`, `targetSdk 36`, `compileSdk 36` (minorApiLevel 1).
- Новые зависимости добавляются только через `gradle/libs.versions.toml`.
- Логика вычисления suspend-множества вынесена в чистый Kotlin-класс без Android-зависимостей и покрыт unit-тестами.

## Сборка и тесты

```bash
# Сборка debug-APK
./gradlew assembleDebug

# Unit-тесты (Android-модули: логика allowlist / защищённых пакетов; JVM-модули :core:model, :core:context)
./gradlew testDebugUnitTest :core:model:test :core:context:test
```

APK собирается в `app/build/outputs/apk/debug/app-debug.apk`.

## Provisioning Device Owner на эмуляторе

Назначение Device Owner (для отладки, без NFC/QR-provisioning):

```bash
adb shell dpm set-device-owner com.numenlabs.zerophone/.ZeroDeviceAdminReceiver
```

Условия применения команды:

- **Свежий эмулятор без привязанных аккаунтов** (только что созданный AVD, без Google-аккаунта и любых других аккаунтов на устройстве).
- На устройстве не должно быть других активных device admins.
- Приложение должно быть установлено (`adb install -r app-debug.apk`) **до** выполнения команды.
- Ожидаемый вывод: `Success: Device owner set to package com.numenlabs.zerophone` (или аналогичный).

## Снятие Device Owner / admin

```bash
adb shell dpm remove-active-admin com.numenlabs.zerophone/.ZeroDeviceAdminReceiver
```

После снятия admin-права блокировка перестаёт применяться; все suspended-пакеты следует предварительно разблокировать через emergency unlock или экран allowlist (либо просто удалить приложение и перезагрузить эмулятор — fresh AVD не пострадает).

## Предупреждение о безопасности

- **Тестируйте сначала на эмуляторе.** Назначение Device Owner и массовая блокировка приложений — потенциально опасные операции; на реальном устройстве ошибка может лишить доступа к приложениям.
- MVP **не использует** `wipeData()`, `lockNow()` и другие необратимые вызовы DevicePolicyManager.
- Системные критические пакеты (`com.android.phone`, `com.android.dialer`, `com.android.settings`, `com.android.systemui`, `com.android.packageinstaller`/`installer` и т.п.), активный IME, лаунчер по умолчанию и само приложение ZeroPhone **никогда не блокируются**.
- Код корректно работает **без** Device Owner — просто не блокирует (см. «Условия работы»).

## Тест-план

1. **Сборка.** `./gradlew assembleDebug` — завершается `BUILD SUCCESSFUL`, APK создан.
2. **Unit-тесты.** `./gradlew testDebugUnitTest` — все тесты логики allowlist/защищённых пакетов зелёные.
3. **Установка.** Создать свежий AVD (без аккаунтов), запустить, установить: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. **Домашний экран.** Нажать «Домой», выбрать ZeroPhone в качестве лаунчера (Always). Убедиться, что на экране отображаются только allowlist-приложения.
5. **Назначение DO.** `adb shell dpm set-device-owner com.numenlabs.zerophone/.ZeroDeviceAdminReceiver` — ответ `Success`. Предупреждение «Device Owner не назначен» исчезает из UI. (Проверить также поведение ДО назначения: предупреждение видно, ничего не блокируется.)
6. **Проверка блокировки.** Приложения вне allowlist: иконка приглушена/запуск невозможен (Google Play и др.), `adb shell dumpsys package <pkg> | grep -i suspend` показывает `suspended=true`. Приложения из allowlist запускаются тапом с домашнего экрана.
7. **Экран allowlist.** Открыть управление allowlist: поиск по названию, включение/выключение чекбоксов — блокировка применяется немедленно (проверить, что приложение заблокировалось/разблокировалось без перезагрузки).
8. **Emergency unlock.** Нажать «Экстренная разблокировка на 30 минут»: все заблокированные приложения становятся доступны, отображается обратный отсчёт до re-lock. Дождаться истечения окна (или ускорить тест, временно уменьшив длительность) — блокировка применяется автоматически.
9. **Перезагрузка.** `adb reboot`: после загрузки политика блокировки переприменяется (`BOOT_COMPLETED`); если emergency-окно было активно — re-lock пере-планирован, отсчёт продолжается.
10. **Снятие admin.** `adb shell dpm remove-active-admin com.numenlabs.zerophone/.ZeroDeviceAdminReceiver` — приложение перестаёт блокировать, работает как обычный лаунчер.
