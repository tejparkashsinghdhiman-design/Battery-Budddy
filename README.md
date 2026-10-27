# Battery Buddy

An anime character that sits on top of your status-bar battery icon (Android 11+, built for Android 15).

- Search any character in the app (AniList API): their portrait becomes the head of a little buddy with swinging legs.
- Or use your own image; tick the "full-body transparent PNG" switch to draw it as-is.
- Built-in original neko girl by default. Happy while charging, worried at 15% or below.
- Drawn with an accessibility overlay (the only way to draw above the status bar). It never reads your screen.

Setup: open in Android Studio, run on your phone, then enable Battery Buddy under Settings > Accessibility.
Tune size/position with the sliders. On aggressive battery managers (realme, etc.) set the app to unrestricted.

APK: every push to `main` builds the app on GitHub Actions and publishes `app-debug.apk` under Releases.
