# Philips 70PUH8215/96 — Pure TV setup

TP Vision platform `TPM191E` (board `PH9M_EA_5599`), Android TV 11 (SDK 30), firmware
`RTT2.211108.001`, MediaTek, 32-bit ARM (`armeabi-v7a`), about 2 GB RAM. Set up on 26 October 2026 over
the network from a Windows PC. The TV is at `192.168.1.116` on its built-in Ethernet.

What was done, all reversible:

| Change | Detail |
|---|---|
| Debloat | 44 packages switched off with `pm disable-user` (never uninstalled), listed with reasons in [`debloat.txt`](debloat.txt) |
| Home screen | Pure TV (`dev.vardxn.puretv`), built from this repo, is the default Home; the stock `com.google.android.tvlauncher` is disabled |
| Dock | SmartTube, Netflix, Prime Video, Max, Spotify, Twitch ([`themes/philips-70puh8215/config.json`](../../themes/philips-70puh8215/config.json)) |
| Weather | Location detected from the internet connection (Taipei), Open-Meteo forecast |
| Ad blocking | Private DNS `dns.adguard-dns.com` for the whole TV |
| YouTube without ads | SmartTube 32.68 stable (`org.smarttube.stable`), next to the official YouTube app |
| AirPlay | `io.github.jqssun.airplay` 0.0.36 (UxPlay, GPL-3), the author's signed release; shows up as "Philips TV", starts at boot |
| Kept on purpose | Chromecast, DTS Play-Fi, DLNA, Live TV/tuner, HDMI inputs, keyboards, Play Store and Play Services |

The backup taken before any change (package lists, settings, getprop) is in `backup/philips-70puh8215/`
in this repo folder (not committed: it holds the TV's serial and IDs).

## Before running any command

Every command needs **USB debugging** switched on again on the TV: Settings → Device Preferences →
Developer options → **USB debugging**. Accept "Allow USB debugging?" if it appears. Switch it off again
when you're done. Run the commands from this repo folder in PowerShell.

## Turn one app back on

```powershell
$env:TV_HOST = "192.168.1.116"
tools\venv\Scripts\python.exe -I tools\tv.py "pm enable com.disney.disneyplus"
```

Use any package name from [`debloat.txt`](debloat.txt). To keep it on for good, put a `#` in front of its
line there, so a later run of the list doesn't switch it off again. The TV's own Settings → Apps → See all
apps → (app) → Enable works too.

## Undo everything

```powershell
powershell -ExecutionPolicy Bypass -File examples\philips-tpm191e\restore.ps1 -TvHost 192.168.1.116
```

Switches every listed app back on, makes the stock home the default again and turns AdGuard DNS off. Add
`-Uninstall` to also remove Pure TV, SmartTube and AirPlay.

## Change the dock order

The quickest way is on the TV: **press and hold OK** on a dock tile and pick the app that should go
there. If you choose an app that is already in the dock, the two apps swap places.

To set the whole order from the PC, edit `dock_apps` in
[`themes/philips-70puh8215/config.json`](../../themes/philips-70puh8215/config.json) (up to 8 apps; the
dock shows `columns` of them, the rest in the row below) and push it:

```powershell
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.116 themes\philips-70puh8215
```

A changed list replaces the dock once. Changes you make afterwards on the TV stay until the list changes
again.

## Change the wallpaper

Put a dark 1920×1080 JPEG of about 300 KB or less into `themes\philips-70puh8215\wallpaper.jpg` and push
the theme with the same `theme.ps1` command. Delete the file and push again to go back to the built-in
picture.

## Rebuild and reinstall

```powershell
powershell -ExecutionPolicy Bypass -File examples\philips-tpm191e\install.ps1 -TvHost 192.168.1.116 [-AirPlay]
```

Builds Pure TV, installs it as Home and pushes this TV's theme; `-AirPlay` also downloads and
reinstalls the AirPlay receiver (building it from source needs Linux or macOS).

## AdGuard DNS

On: `settings put global private_dns_specifier dns.adguard-dns.com`, then
`settings put global private_dns_mode hostname`.
Off: `settings put global private_dns_mode opportunistic` (or Settings → Network → Private DNS).
It blocks ad and tracking servers in many apps. **It does not block YouTube ads**, which come from the same
servers as the videos; SmartTube covers those. If a streaming app ever refuses to play or sign in, switch
it off and try again.

## Known limits on this TV

- With Google Assistant off (by choice), the remote's microphone button does nothing.
- Firmware updates are off (`org.droidtv.oad`). To update, enable it, update in Settings, then disable it
  again.
- Screenshots of Live TV, HDMI and Netflix come out empty: the TV protects that content.
- AirPlay mirrors the screen and plays YouTube/audio from an iPhone, but not DRM video (Netflix, Apple TV+).
  It comes to the front by itself when a phone connects (needs "display over other apps", which
  `install.ps1 -AirPlay` grants), but stays open after mirroring stops: press **Back** or **Home**. The app
  has no setting to close itself on disconnect.
- The remote's app-drawer key (Philips key 602) normally opens the Philips TV Collection drawer, which is
  disabled here; Pure TV answers it instead and opens the TV's Settings. Re-enabling
  `org.droidtv.philipstvdrawer` makes the TV ask which of the two to use.
