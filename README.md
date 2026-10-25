# SkinSewa Android

Native Android client for the SkinSewa Laravel API, built with Kotlin and Jetpack Compose. The app includes a branded splash screen, API-backed login and invitation OTP registration, forgot-password requests, a signed-in dashboard, encrypted token persistence, and a saved light/dark appearance setting. Light mode is the default and does not follow the device theme.

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

The debug and release builds default to the deployed API at `https://api.ricky.com.np/api/v1/`. To point a debug build at a local API instead, pass `-PTELEDERM_API_BASE_URL=http://10.0.2.2:8000/api/v1/`; this is the Android emulator's host loopback address. Start the local API with `php artisan serve --host=0.0.0.0` from `telederm-api`. Cleartext HTTP is enabled only for the debug build. The app rejects non-HTTPS API URLs except emulator loopback.

Configure a deployed API base URL for release builds with:

```sh
./gradlew :app:assembleRelease -PTELEDERM_API_BASE_URL=https://api.example.com/api/v1/
```

The URL must include the `api/v1/` prefix and end with a slash. Never put credentials or OTPs in the URL.

## Current behavior

- The splash screen uses the SkinSewa mark, then routes to the signed-in account page if the saved token is still accepted, or to sign-in otherwise.
- Login and forgot-password requests use the deployed Laravel `/api/v1/auth` endpoints. Registration submits the invited email, six-digit OTP, account details, and password to `/api/v1/auth/register`; the server assigns the invitation's role and returns a bearer token.
- Access tokens are encrypted with AES-GCM using an Android Keystore key.
- The dashboard displays the authenticated user's role and available profile details. Settings lets the user choose Light or Dark; the setting is saved across launches.
- The dashboard shows live user, case, resource, and completed-case totals. Administrator metrics are system-wide; care-team case counts follow the signed-in user's API access. Its navigation drawer shows shared workspace links and expands administrator tools only for `ADMIN` users. Resources are listed from the authenticated API, paginated, and opened in the device's PDF viewer. Administrators can add resources, edit names or replace PDFs, and delete resources from the resource library.
- The dark palette uses Material 3 surface and on-surface roles with light blue and teal accents. It avoids pure black backgrounds to preserve layered surfaces and readable content.

Case and chat flows can be added against their backend routes as those modules are implemented.
