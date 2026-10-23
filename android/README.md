# Room for Android (preview v0.2.0)

This is a native Kotlin/Jetpack Compose Android app for the Room listening dashboard. It does not render the website in a WebView.

## Current preview features

- Overview, track rankings, artist rankings, genre galaxy, and activity screens.
- Last.fm profile connection with 24-hour, 7-day, 1-month, 1-year, and all-time ranges.
- Locally processed Spotify listening-history JSON import and clear-import action.
- Public artwork enrichment using Last.fm, iTunes, and Deezer endpoints, with in-memory bitmap caching.
- Animated Room record/orbit hero, theme colors, animated genre bubbles and artist portraits.
- Local track ratings/notes and story, square, and track PNG cards shared through Android's share sheet.
- Last.fm Spotify-linking settings. Room never asks for Spotify credentials.

This is an Android preview, not a Play Store-ready release and not yet a complete feature-for-feature replacement for the website. The website has more detailed data visualizations and its own fully animated galaxy/share-card renderer. Artwork providers can return no result, and image fetching requires an internet connection. Test carefully before relying on it.

## Build and test

Open this folder in Android Studio with Java 17 and Android SDK 37 installed, sync Gradle, and run the `app` configuration on a device/emulator. The app uses application ID `com.abdu2l.room`, minimum SDK 23, and target SDK 35.

To build a local debug APK, run `:app:assembleDebug`; it is written to `app/build/outputs/apk/debug/app-debug.apk`. A debug APK can be installed on a personal Android device after allowing installation from the file manager/browser used to open it. It is not the signed Play Store bundle.

For a Play Store release, create a signed Android App Bundle with a private release keystore, meet the current Play target API/policy requirements, add a privacy-policy URL, and complete the Play Console listing and data-safety declarations. Never commit the signing key.
