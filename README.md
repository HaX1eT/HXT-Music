# HXT Music (Android, Kotlin)

Нативный плеер: Jetpack Compose + Media3 (ExoPlayer, уведомление, экран блокировки) +
NewPipeExtractor (поиск YouTube Music и ссылки на аудио — без сервера и без yt-dlp).

Как в веб-версии: поиск → трек скачивается ОДИН раз в `files/library/audio`, обложка в `covers`,
дальше играет локально. Лайк = трек сохранён. Liked с ручным порядком (карандаш), импорт списка (шестерёнка).

## Сборка в GitHub Actions
1. Создайте репозиторий и залейте содержимое этой папки (вместе с `.github/`).
2. Вкладка **Actions → Build APK → Run workflow** (или просто push в `main`).
3. Готовый APK: **Artifacts → HXT-Music-apk**. По тегу `v1.0` APK ещё и попадёт в Releases.

### Своя подпись (необязательно, нужна для обновления поверх)
```
keytool -genkeypair -v -keystore hxt.jks -alias hxt -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 hxt.jks     # вывод -> секрет KEYSTORE_BASE64
```
Секреты репозитория: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Без них APK подписывается debug-ключом (ставится, но обновление поверх старой сборки с другого раннера потребует переустановки).

## Локально
`gradle :app:assembleRelease` (Gradle 8.9, JDK 17). Шрифты Unbounded/Manrope положите в
`app/src/main/res/font/unbounded.ttf` и `manrope.ttf` (в CI они скачиваются сами; без них — системный шрифт).

## Если перестал работать поиск/загрузка
YouTube периодически меняет API. Обновите версию в `app/build.gradle.kts`:
`com.github.TeamNewPipe:NewPipeExtractor:v0.24.6` → последняя с https://github.com/TeamNewPipe/NewPipeExtractor/releases
