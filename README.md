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
- **Play on TV**: from the phone, send a channel (and its list) to Stream Desk on a TV box over the home Wi-Fi, then use the
  phone as a remote (CH +/−, stop). Also sends the IPTV login and Sport list to the TV, so nothing is typed with the remote.
  The TV announces itself on the network and shows a QR code and a 6-digit pairing code (cast icon → Link a phone); wrong
  codes are locked out. Scanning the QR code with the phone's camera opens a page on the TV with a button that starts
  Stream Desk on the phone (`streamdesk://connect?host=…&code=…`, accepted only for home-network addresses) and offers to
  send the login.
- **Auto-reconnect**: a live stream that drops reconnects up to 5 times (after 1, 2, 4, 8 and 16 s).
- Opens stream links shared from other apps or tapped in a browser, and `.m3u` files from a file manager.

Not supported: UDP/RTP multicast, SRT, MMS and DRM-protected streams.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```sh
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
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
