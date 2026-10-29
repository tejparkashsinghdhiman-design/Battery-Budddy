# Battery Buddy

A cute anime character that sits on your status-bar battery icon (Android 11+, built for Android 15).

## Pick a character

- **Top 200**: the most-loved anime characters on AniList, ranked, with a Load more button for even more.
- **Search** any character by name (Dazai, Johan, Zero Two...).
- **My buddies**: everyone you have picked is saved on the phone and works offline.
- **Surprise me** picks a random top character. **My photo** uses your own picture.
- **Build your own** original buddy: 6 presets (Neko girl, Bunny girl, Fox girl, Spiky hero, Cool guy, Silver mage) or mix hair style, hair colour, skin, eyes, outfit colour, ears and tail, and accessory.

## 2D and 3D

- **2D** flat cartoon look, or a **3D figurine** with real perspective turntable rotation, depth parallax, shading and a hair shine. It sways on its own or follows your phone's tilt. Animation speed from 50% to 200%.

## Safety features

- **Unplug reminder** at a charge limit you choose (default 80%).
- **Overheat warning** at 43 °C or when Android reports overheating.
- **Quick Settings tile** as an instant off switch.
- Never blocks touches, rests at 15% battery, hides adult-title characters by default, never reads the screen, no analytics, no backups.

## Setup

Grab `app-debug.apk` from Releases (built by GitHub Actions on every push to `main`), install it, then enable Battery Buddy under Settings > Accessibility. Tune size and position on the Home tab. On aggressive battery managers (realme, etc.) set the app to unrestricted.

The UI is Jetpack Compose with Material 3. Character data and pictures come from the AniList API.
