## Что сделано

<!-- Краткое описание изменений и мотивация. -->

## Чеклист перед merging

- [ ] `./gradlew assembleDebug` проходит локально
- [ ] `./gradlew testDebugUnitTest` проходит локально
- [ ] Поведение Device Owner сохранено: `setPackagesSuspended` только под Device Owner, safe no-op без него
- [ ] Не используются `wipeData()` / `lockNow()` и другие необратимые вызовы DevicePolicyManager
- [ ] Системные пакеты (`com.android.*`, критические, активный IME, дефолтный лаунчер, само приложение) не блокируются
- [ ] adb-provisioning (`dpm set-device-owner`) не сломано
- [ ] Emergency-unlock: 30 минут + авто re-lock (AlarmManager + BOOT_COMPLETED) работает
- [ ] Новые зависимости объявлены только через `gradle/libs.versions.toml`
- [ ] Suspend-/чистая Kotlin-логика покрыта unit-тестами
- [ ] Пользовательские строки вынесены в ресурсы (`strings.xml`)

## Как проверял

<!-- Шаги тест-плана из README (или другие проверки), которые выполнены. -->
