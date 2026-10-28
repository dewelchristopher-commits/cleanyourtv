# Barça

FC Barcelona on the TV: a full-screen dashboard with the next game and its countdown, the two games after it, the
last five results and the LaLiga table around Barça, plus Barça's next games in the launcher's `game_day` panel on
Home ("Barça – Girona · LaLiga · 1er, 31 pts", then "Coup d'envoi dans 2 j 4 h", then "En direct"). The data comes
from [football-data.org](https://www.football-data.org/) with your own free key; the screens are in French.

## Setting it up

1. Make a free account on football-data.org and copy the key from the e-mail it sends.
2. Build and install the app (below), then send it the key from the computer, once:

```bash
adb shell am broadcast -n io.github.dewelchristopher.barca/.KeyReceiver -a io.github.dewelchristopher.barca.SET_KEY --es key <your key>
```

It answers `OK : … matchs, … equipes au classement` when the key works.

3. On TCL sets, let the launcher start the app's provider (`TclAppBoot`, see `addons/game-day`):

```bash
adb shell "appops set io.github.dewelchristopher.barca AUTO_START allow; appops set io.github.dewelchristopher.barca APP_AUTO_START allow; appops write-settings"
```

4. In a theme, set `"game_day": "io.github.dewelchristopher.barca"` for the panel, and put the package in
   `dock_apps` for a tile that opens the dashboard (`themes/barca` does both).

## Build and install

JDK 17 and Android SDK platform 34, as for the launcher. From this folder: `./gradlew assembleRelease`
(`gradlew.bat assembleRelease` in Command Prompt), then
`adb install -r app/build/outputs/apk/release/app-release.apk`.

## Refreshes

Two requests per refresh (Barça's season and the LaLiga table), at most every three hours, every 20 minutes
once a game in the cached copy has started, and when the dashboard opens with a copy over 15 minutes old. The free
tier allows ten requests a minute. A failed refresh keeps the last good copy and tries again 15 minutes later.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Only to read Barça's matches and the LaLiga table from api.football-data.org with your key. |

The key stays in the app's private storage and is never shown or shared with Home. It arrives through a receiver
that needs `WRITE_SECURE_SETTINGS`, which only the ADB shell holds.
