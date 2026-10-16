# SkinSewa Android

Native Android client for the SkinSewa Laravel API, built with Kotlin and Jetpack Compose. The current app includes a branded splash screen, API-backed sign-in, forgot-password requests, a signed-in account page, and a persisted light/dark appearance setting. Light mode is the default and does not follow the device theme.

## Toolchain

- Android Studio JBR / JDK 21
- Gradle wrapper 9.4.1
- Android Gradle Plugin 9.2.1 with its bundled Kotlin 2.2.10 support
- Android SDK 36
- Minimum Android API 24

Open this folder in Android Studio, or build the emulator debug APK with:

```sh
./gradlew :app:assembleDebug
```

The debug build defaults to `http://10.0.2.2:8000/api/v1/`, the Android emulator's host loopback address. Start the API locally with `php artisan serve --host=0.0.0.0` from `telederm-api`. Debug cleartext HTTP is enabled for this build variant only. The app rejects non-HTTPS API URLs except emulator loopback.

Configure a deployed API base URL for release builds with:

```sh
./gradlew :app:assembleRelease -PTELEDERM_API_BASE_URL=https://api.example.com/api/v1/
```

The URL must include the `api/v1/` prefix and end with a slash. Never put credentials or OTPs in the URL.

## Current behavior

- The splash screen uses the SkinSewa mark, then routes to the signed-in account page if the saved token is still accepted, or to sign-in otherwise.
- Login and forgot-password requests use the Laravel `/api/v1/auth` endpoints.
- Access tokens are encrypted with AES-GCM using an Android Keystore key.
- The account page displays the authenticated user's role and available profile details. Settings lets the user choose Light or Dark; the setting is saved across launches.
- The dark palette uses Material 3 surface and on-surface roles with light blue and teal accents. It avoids pure black backgrounds to preserve layered surfaces and readable content.

Additional case, chat, resource, and administrator flows can be added against backend routes as those modules are implemented.
