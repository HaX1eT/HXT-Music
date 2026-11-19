# HXT Music (Android, Kotlin)

Нативный плеер: Jetpack Compose + Media3 (ExoPlayer, уведомление, экран блокировки) +
NewPipeExtractor (поиск YouTube Music и ссылки на аудио — без сервера).

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
