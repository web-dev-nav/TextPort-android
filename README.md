# TextPort Android App

Android client for TextPort SMS synchronization.

## What this app does

- Captures incoming SMS (`RECEIVE_SMS`, `READ_SMS`)
- Registers device using activation code
- Syncs SMS to TextPort Laravel API in background via WorkManager
- Lets user toggle sync on/off
- Supports server connection testing and settings from app UI

## Current UX flow

1. Splash screen
2. First-run SMS permission prompt
3. First-run connection verification prompt
4. Dashboard + hamburger menu navigation
5. Device registration (activation code)
6. Live sync toggle

After first successful setup, registration is hidden from dashboard and profile/settings controls are used for account reset or connection changes.

## Build / run

```bash
cd /path/to/TextPort-android
./gradlew :app:installDebug
```

## Required permissions

- `android.permission.RECEIVE_SMS`
- `android.permission.READ_SMS`
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.RECEIVE_BOOT_COMPLETED`

## API base URL

Default is set in `app/build.gradle.kts` via `BuildConfig.API_BASE_URL`.

You can also change/test the server URL in-app from **Settings**.

## Device onboarding

Primary:
- Admin creates account/code from backend admin panel (`/admin/accounts`)
- User enters code in app and registers

Optional hybrid:
- App can request code automatically if backend policy enables:
  - `AUTH_ALLOW_PUBLIC_CODE_REQUEST=true`

## Background behavior

- SMS receiver + WorkManager continue in background for normal use.
- After device reboot, boot receiver restores sync context.
- If app is force-stopped by user, Android blocks background receivers until app is opened again.

## Troubleshooting

- If sync fails, first test connection from app Settings.
- Confirm sync toggle is ON.
- Confirm device is registered (activation successful).
- Check backend admin logs and device feed for API-side errors.
