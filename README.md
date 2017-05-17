# Funkytunes

![Screenshots](screenshots.png)

Funkytunes is a BitTorrent-based music app for Android, like Spotify or
Pandora. You can stream any music for free. The app is completely
open-source.

[![Download](download.png)](https://github.com/funkyg/funkytunes/releases/latest)

[Gitlab Mirror](https://gitlab.com/funkydev/funkytunes)

# How does it work?

The initial album list is simply fetched from the
[iTunes Album Charts](https://www.apple.com/itunes/charts/albums/).

The search functionality uses the [Discogs SearchAPI](https://www.discogs.com/).

After clicking an album, the app searches for `artist album` on
[ThePirateProxy](https://theproxypirate.pw) (limited to music). It then takes
the magnet link from the first search result, and downloads the torrent file.
Finally, the first mp3 file in the torrent is downloaded and played as soon
as its ready. Additional songs are downloaded on demand.

# Building

Funkytunes uses a standard Android build. Just install Android Studio and
Android SDK, import the project, and that's it!

# License

Funkytunes is licensed under [GPLv3](LICENSE)
