# Battery Buddy

An anime character that sits on top of your status-bar battery icon (Android 11+, built for Android 15).

- Search any character in the app (AniList API): their portrait becomes the head of a little buddy with swinging legs.
- Or use your own image; tick the "full-body transparent PNG" switch to draw it as-is.
- Built-in original neko girl by default. Happy while charging, worried at 15% or below.
- Drawn with an accessibility overlay (the only way to draw above the status bar). It never reads your screen.

## Safety features

- **Charge-limit alert**: notifies you to unplug at a limit you choose (default 80%), which is gentler on battery lifespan.
- **Overheat warning**: notification at 43 °C or when Android reports overheating; the buddy turns red and sweats.
- **Instant off switch**: add the "Battery Buddy" tile to Quick Settings to hide her in one tap.
- **Never blocks touches**: the overlay is non-touchable and size-capped.
- **Rest mode**: at 15% or below (not charging) she stops animating to save power.
- **Adult-title filter**: search results from adult titles are hidden by default.
- **Privacy**: no screen reading, no analytics, no backups; the only network use is the character search (HTTPS).
- **Crash guard**: if Android refuses the overlay, the service idles instead of crashing.

## Setup

Open in Android Studio and run on your phone, or grab `app-debug.apk` from Releases (built by GitHub Actions on every push to `main`).
Then enable Battery Buddy under Settings > Accessibility and tune size/position with the sliders.
On aggressive battery managers (realme, etc.) set the app to unrestricted.
