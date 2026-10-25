# Contributing to Pure TV

Thanks for trying Pure TV. The most useful things you can do:

## 1. Tell us how it ran on your TV

Open an issue with the **TV report** template: the model (the installer's `probe` command prints it), what worked and what didn't. Every report makes the README's "works on" list more trustworthy for the next person.

## 2. Add a debloat list for your TV

The installer's *Debloat* step reads `examples/<brand>-<model>/debloat.txt`. If your TV isn't there:

1. Copy the format of an existing list: one package per line, grouped with a comment that says what the group is.
2. Put the exact model, Android version and firmware build in the header.
3. Only list packages you have switched off and lived with for a few days, and note anything you had to switch back on.
4. Never list packages to *uninstall*: everything here uses `pm disable-user`, which the TV's own Settings can undo.

Most lists come from the [cleanyourtv](https://github.com/Gathin23/cleanyourtv) project; adding yours there helps both projects.

## 3. Improve the launcher

The app lives in `launcher/`: plain Java and AndroidX, no Kotlin or Compose, built for TVs with 1–2 GB of RAM.

- Measure on a real TV before and after a performance change, and put the numbers in the pull request.
- Keep the remote's D-pad navigation predictable from every edge.
- Anything specific to one TV goes in `launcher/app/src/main/res/values/config.xml`, not in code.
- Include a screenshot from the TV for any visual change (`tools/tv.py 'screencap -p /sdcard/s.png'`, then `tools/tv.py --pull /sdcard/s.png s.png`).

Build with `cd launcher && ./gradlew assembleRelease` (JDK 17, Android SDK 34). CI runs the unit tests, lint and a build on every pull request.

## Pull requests

One topic per pull request, small is fine, and say which TV you tested on. By contributing you agree your work is released under the [MIT licence](LICENSE).
