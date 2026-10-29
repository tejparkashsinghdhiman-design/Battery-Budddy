# Battery Buddy

An anime character that sits on top of your status-bar battery icon (Android 11+, built for Android 15).

## Characters

- **Search any anime character** in the app (AniList API): their portrait becomes the head of a little buddy with swinging legs.
- **Build your own** built-in character: 6 presets (Neko girl, Bunny girl, Fox girl, Spiky hero, Cool guy, Silver mage) or mix hair style (4), hair colour (8), skin (4), eyes (6), outfit colour (8), ears and tail (cat / bunny / fox / none) and accessory (bow / glasses / headphones / none).
- **Your own image**: pick any picture, or tick the "full-body transparent PNG" switch to draw it as-is.

## 2D and 3D

- **2D**: flat, clean cartoon look.
- **3D figurine**: real perspective turntable rotation, depth parallax between body parts, gradient shading and a hair shine. It sways on its own or follows your phone's tilt. Works on every character type.
- Animation speed from 50% to 200%. Happy while charging, worried at 15% or below.

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
