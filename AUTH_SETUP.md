# Auth Setup (Email, Google, Facebook)

Impulsive uses Firebase Authentication as the backend for Email, Google, and Facebook sign-in. Guest mode is local-only and continues to work even when Firebase is not configured.

## 1. Firebase project

1. Go to <https://console.firebase.google.com/> and create a Firebase project.
2. Open Project settings, then General, then Your apps, then Add app, then Android.
3. Use Android package name `com.impulsive.app`. This must match exactly.
4. Run `./gradlew signingReport` from the project root and copy the `SHA1` value from the debug variant into Firebase. This is required for Google Sign-In.
5. Download the generated `google-services.json` and place it at `app/google-services.json`.
6. Delete `app/google-services.json.PLACEHOLDER` after the real file is present.

Without a real `google-services.json`, provider sign-in is disabled gracefully and the app shows "Authentication is not configured yet. Continue as guest for now." The placeholder file exists only to make the missing Firebase config obvious.

## 2. Enable Firebase providers

In Firebase Console, open Build, then Authentication, then Sign-in method, then Add new provider.

Enable these providers:

- Email/Password: enable it.
- Google: enable it and choose a project support email. Firebase will create a "Web client (auto created by Google Service)" OAuth client. Copy its Client ID. It ends with `.apps.googleusercontent.com`.
- Facebook: enable it. Create a Facebook App at <https://developers.facebook.com/apps/>, add the Facebook Login product, and paste the App ID and App Secret into Firebase. Firebase will display an OAuth redirect URI. Add that URI in the Facebook app under Facebook Login, then Settings, then Valid OAuth Redirect URIs.

## 3. Fill in `strings.xml`

Open `app/src/main/res/values/strings.xml` and replace each placeholder:

| String | Where to get it |
| --- | --- |
| `facebook_app_id` | Facebook App dashboard, Settings, Basic, App ID |
| `facebook_client_token` | Facebook App dashboard, App Settings, Advanced, Client token |
| `fb_login_protocol_scheme` | `fb` plus your Facebook App ID, for example `fb1234567890` |
| `default_web_client_id` | Firebase or Google Cloud "Web client" OAuth Client ID |

Do not invent these values. Leave the placeholders in place until the real project credentials exist. The app checks for placeholder values and shows the configuration error instead of launching a broken provider flow.

## 4. Facebook key hashes

Facebook Login requires each APK signing key hash to be registered.

Debug:

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore \
  | openssl sha1 -binary | openssl base64
```

Release:

```bash
keytool -exportcert -alias YOUR_RELEASE_ALIAS -keystore /path/to/release.jks \
  | openssl sha1 -binary | openssl base64
```

Paste the resulting base64 strings into the Facebook app under Settings, Basic, Android, Key Hashes.

## 5. Test

1. Run `./gradlew :app:assembleDebug`.
2. Install on a device or emulator. Google sign-in requires Google Play Services.
3. Let the intro finish and wait for the login screen.
4. Tap Google. With a complete Firebase setup it should open the real Credential Manager Google flow; with placeholders or missing Firebase config it should show the clear configuration error.
5. Tap Facebook. With real Facebook strings, Firebase Facebook enabled, and registered key hashes it should open the Facebook Login flow; with placeholders it should show the clear configuration error.
6. Tap "Continue as guest". This should continue into onboarding even when Firebase is not configured.

The "Create account" and "Log in" buttons use Firebase Email/Password auth.

## File map

- `app/build.gradle.kts`: Firebase Auth, Credential Manager, Google Identity, Facebook Login.
- `build.gradle.kts`: Google Services plugin, applied by the app only when `app/google-services.json` exists.
- `app/src/main/AndroidManifest.xml`: Internet permission and Facebook activities.
- `app/src/main/res/values/strings.xml`: provider configuration strings.
- `app/src/main/res/drawable/ic_google.xml`, `ic_facebook.xml`: provider marks used by the login screen.
- `app/src/main/java/com/impulsive/app/backend/domain/model/auth/`: `AuthUser`, `AuthProvider`.
- `app/src/main/java/com/impulsive/app/backend/data/repository/AuthRepository.kt`: auth interface and `AuthResult`.
- `app/src/main/java/com/impulsive/app/backend/data/repository/FirebaseAuthRepository.kt`: provider auth implementation.
- `app/src/main/java/com/impulsive/app/backend/session/auth/`: `AuthState`, `AuthViewModel`.
- `app/src/main/java/com/impulsive/app/frontend/screens/onboarding/LoginScreen.kt`: login UI.
- `app/src/main/java/com/impulsive/app/MainActivity.kt`: forwards `onActivityResult` for Facebook.
