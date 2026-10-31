# Stream Desk for Android

Open a network stream by link, like VLC's "Open Network Stream", and keep a channel list.

## Features

- Plays **HLS** (`.m3u8`), **DASH** (`.mpd`), **RTSP** (IP cameras), **RTMP** and plain video files (MP4, MKV, WebM, TS…).
  Links without a clear extension are tried as HLS and then as DASH. You can also pick the format by hand.
- **Channel list** with search, groups, favorites and recently played.
- **Playlist links**: add the link to an M3U channel list (for example from an IPTV provider). The app keeps the link,
  updates it once a day and on demand, keeps your favorites across updates, and can remove a playlist with its channels.
  Pasting a playlist link into the main box is detected and loaded as a playlist instead of played.
- **IPTV sign-in** (Xtream Codes): server, username and password. Loads Live TV, Movies and Series (episodes listed
  when you open a series), shows the account's expiry date and screen limit, and refreshes daily. Pasting a
  `get.php?username=…&password=…` link opens the sign-in with the fields filled in.
- **M3U / M3U8 files**: one-time import from a file or pasted text, and export or share.
  Reads `tvg-logo`, `group-title`, `#EXTGRP`, `http-user-agent` / `http-referrer` and `#EXTVLCOPT` header options.
- **Player**: full screen, picture-in-picture, video quality picker, subtitles and audio tracks,
  previous/next channel, and a stream info panel (resolution, codec, bitrate, buffer, dropped frames, delay behind live).
- **TV mode** (Android TV / Google TV boxes such as the Xiaomi Mi Box): appears on the TV home screen, larger UI, a
  highlight that follows the remote, ▲/▼ to change channel, OK for the channel list, Menu for quality or stream info.
  Switches on automatically on a TV; phones are unchanged.
  Number keys type a channel number (3, or 1 then 2 for 12; OK goes at once). Fast-forward and rewind also change channel.
  The Logs screen lists each remote key the app receives ("Remote key: …"), to see what a TV's remote passes on over HDMI.
- **Play on TV**: from the phone, send a channel (and its list) to Stream Desk on a TV box over the home Wi-Fi, then use the
  phone as a remote (CH +/−, stop). Also sends the IPTV login and Sport list to the TV, so nothing is typed with the remote.
  The TV announces itself on the network and shows a QR code and a 6-digit pairing code (cast icon → Link a phone); wrong
  codes are locked out. Scanning the QR code with the phone's camera opens a page on the TV with a button that starts
  Stream Desk on the phone (`streamdesk://connect?host=…&code=…`, accepted only for home-network addresses) and offers to
  send the login.
- **Logs from TV**: on the phone, ⋮ → Logs from TV shows the TV's activity log (passwords hidden) with a Copy button, since copying on a TV box goes nowhere.
- **Continue on phone**: ⋮ → Continue from TV on the phone (or Play here on the phone's remote screen) stops the TV and carries on
  with the same channel on the phone, so only one screen uses the account at a time.
- **Sound with the screen off** (phone): the channel's sound keeps playing when the screen turns off or you leave the app. A notification shows the channel with a Stop button. ⋮ → Sound with the screen off turns it off, and then a live stream is closed when the app is left.
- **Auto-reconnect**: a live stream that ends, errors or freezes for 25 s reconnects up to 12 times (1, 2, 4 and 8 s apart, then every 8 s).
- **What's on now**: Sport channels show the current program and when it ends, from the IPTV provider's guide.
- **Swipe between tabs** (phone): drag left or right on the channels: they follow your finger, and when you let go the next or previous tab slides in (All, Sport, Israeli TV, Guide, …).
- **Quality on this TV**: on a TV the tiles pick Full HD at most, because the Mi Box takes minutes to show a picture on these 4K streams. ⋮ → Quality on this TV switches 4K back on. 
- **Slow start check**: a channel with no picture after 15 s is tried once more; then the app asks the provider how many screens are in use and says if another screen is playing.
- **Israeli TV tab**: Channel 11 (Kan), 12, 13, 14 and 15, each with its logo and in the best quality, picked the same way as the Sport list. Channels the account doesn't have are left out.
- **TV guide**: the Guide tab lists today's and tomorrow's programs on all Sport channels in time order (OK on a program
  that's on now plays it). Each IPTV channel's menu also has "TV guide" for its full schedule.
- **Channels on screen** (TV): the home screen opens with 12 channel tiles (4 × 3) that fill the screen. ⋮ → Channels on screen cycles 6 / 9 / 12 / 15 / 20.
- **Open on last channel** (TV): off by default, so the app opens on its channels. ⋮ → Open on last channel turns it on.
- **New version notice**: ⋮ → Check for updates (and once when the app opens) reads `dist/update.json` from the repo and says when there's a newer version; on a phone, Download opens it in the browser. The app doesn't install anything itself (no install-other-apps permission), because Play Protect blocks such apps on TV boxes. `scripts/release.sh` publishes a build there. The link must be reachable without signing in, so the repo has to be public.
- Opens stream links shared from other apps or tapped in a browser, and `.m3u` files from a file manager.

Not supported: UDP/RTP multicast, SRT, MMS and DRM-protected streams.

## LG TV without the Mi Box

`webos/` is a separate LG webOS app with the essentials (sign-in, Sport and Israeli TV tabs, what's on now, the player and the Magic Remote). The installable file is `dist/webos/com.streamdesk.tv_<version>_all.ipk`; see `webos/README.md` for installing it with Developer Mode.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```sh
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleRelease        # → app/build/outputs/apk/release/app-release.apk (what gets installed: optimized, opens fast)
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk (for development)
./gradlew testDebugUnitTest      # playlist parser tests
```

Or open the folder in Android Studio and press Run.

## Install on a TV box (Mi Box, Android TV)

Copy `app-debug.apk` to the box with the "Send Files to TV" app (phone + TV), a USB stick, or the Downloader app, then
open it with a file manager and allow "Install unknown apps" for that app. Or: `adb connect <box-ip>:5555` then
`adb install app-debug.apk` (Developer options → network debugging on the box).

## Install on a phone

Copy `app-debug.apk` to the phone and open it (allow "Install unknown apps" for your file manager when asked),
or with USB debugging on: `adb install app/build/outputs/apk/debug/app-debug.apk`.

For the Play Store you need a release build signed with your own key (`./gradlew bundleRelease`).

## Code map

| File | What it does |
| --- | --- |
| `MainActivity.kt` | Channel list screen: open a link, tabs, search, groups, import/export |
| `PlayerActivity.kt` | Media3 ExoPlayer, format fallback, reconnect, PiP, stream info |
| `Channel.kt` | Channel model, M3U parser/writer, format detection |
| `ChannelStore.kt` | Saved channels, playlist links and history (JSON files) |
| `Net.kt` | Playlist downloads |
| `Xtream.kt` | IPTV sign-in: live channels, movies, series, episodes |
| `Theme.kt` | Colors, TV scaling |
| `TvSupport.kt` | TV detection, remote key mapping, focus highlight |
| `Link.kt` | Play on TV: the TV's small server and the phone's client |
| `LinkAndroid.kt` | Finding the TV (NSD), pairing code, handling what the phone sends |
| `LinkUi.kt` | TV picker and "Link a phone" screens |
| `SportList.kt` | The Sport tab: chosen channels, best quality, logos |
| `Epg.kt` | The provider's guide: what's on now, days |
| `GuideUi.kt` | Guide tab and a channel's schedule |
| `Update.kt`, `UpdateUi.kt` | In-app update: check, download, install |
| `Resume.kt` | Open on the last channel (TV) |
