# Battery Buddy

A cute anime character that sits on your status-bar battery icon (Android 11+, built for Android 15).

## Pick a character

- **Top 200**: the most-loved anime characters on AniList, ranked, with Load more for even more.
- **Search** any character by name, **Surprise me**, **My photo**, and **My buddies** (everyone you've picked, saved offline).
- **Build your own** original buddy: 6 presets or mix hair, colours, eyes, outfit, ears and tail, accessory. Halo, crown, wings and star shades unlock as she levels up.
- **2D flat** or **3D figurine** (perspective turntable, parallax, shading, tilt with your phone).

## What she does (all switchable on the Life tab)

- **Battery**: sleeps while charging with a happy dance at 100%, zooms on fast chargers, yawns at 30%, begs at 10%, faints at 5%, shivers with a scarf when the battery is cold, sweats when it is hot.
- **Your day**: nightcap after midnight, coffee in the morning, shades at noon; headphones and notes while music plays; shushes you in Do Not Disturb; festival outfits (Halloween, Christmas, New Year, Valentine's, Diwali, Republic and Independence Day, sakura season).
- **Alive**: idle waves, stretches, looks and bubbles; wanders along the status bar and hides in your camera hole; a pocket cat that hops along.
- **Useful**: speech bubbles with time left or time to full, eye-break pokes every 20 minutes of screen time, a weekly coach report.
- **Tamagotchi**: XP, levels, streaks and a mood meter based on how you treat your battery.
- **Home-screen widget** and a **Quick Settings tile** (instant off switch).
- **Chat** with her through Claude using your own Anthropic API key (stored only on the phone).

## Safety

Unplug reminder at a limit you choose, overheat warning at 43 °C, never blocks touches, adult-title filter, no screen reading, no analytics, no backups.

## Performance

No allocations per frame, adaptive frame rate (slower when sleeping, low on battery or in battery saver), everything pauses when the screen is off, sensors run only when 3D tilt is on and visible, widget updates at most once a minute.

## Setup

Grab `app-debug.apk` from Releases (built by GitHub Actions on every push to `main`), install it, then enable Battery Buddy under Settings > Accessibility. On aggressive battery managers (realme, etc.) set the app to unrestricted.
