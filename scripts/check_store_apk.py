#!/usr/bin/env python3
"""Checks that an APK does (or does not) contain the online-access code of the "full" flavor.

AuraCurve roadmap 02, task 1.3: the "store" flavor is what Google Play and SoundCloud get to see.
It must not contain the unofficial SoundCloud access, the Spotify/YouTube link resolution or the
self-update - not hidden, but absent from the code. This script looks for markers of that code in
the DEX files and the binary manifest of an APK.

    check_store_apk.py store.apk                    # fails if any marker is present
    check_store_apk.py --expect-present full.apk    # fails if a marker is missing (checks the check)
"""

import sys
import zipfile

# Markers in the compiled code (class names and string constants).
DEX_MARKERS = [
    "api-v2.soundcloud.com",  # SoundCloud's internal API (SoundCloudClient)
    "client_id=",  # the scraped SoundCloud web client id (ApiUrl)
    "SoundCloudLoginActivity",  # sign-in in an embedded WebView
    "SpotifyLoginActivity",
    "SpotifyApiClient",
    "youtube.com/oembed",  # YouTube link resolution (LinkResolver)
    "ReleaseUpdateClient",  # self-update from GitHub releases
    "open.spotify.com",  # Spotify link parsing (ShareLink, SpotifyPlaylistPage)
    "SpotifyImportPlan",  # Spotify playlist import
    "SoundCloudLinkImport",  # link import of pasted and shared text
    "api.audius.co",  # Audius tab (full flavor only until its use in the store is cleared)
    "radio-browser.info",  # Radio tab (Radio Browser directory)
]

# Markers in AndroidManifest.xml (binary XML stores its strings as UTF-8 or UTF-16).
MANIFEST_MARKERS = [
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "SoundCloudLoginActivity",
    "SpotifyLoginActivity",
    "spotify-callback",
]


def found_markers(apk_path):
    hits = []
    with zipfile.ZipFile(apk_path) as apk:
        dex = b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))
        manifest = apk.read("AndroidManifest.xml")
    for marker in DEX_MARKERS:
        if marker.encode("utf-8") in dex:
            hits.append(f"dex: {marker}")
    for marker in MANIFEST_MARKERS:
        if marker.encode("utf-8") in manifest or marker.encode("utf-16-le") in manifest:
            hits.append(f"manifest: {marker}")
    return hits


def main(argv):
    expect_present = "--expect-present" in argv
    paths = [a for a in argv if not a.startswith("--")]
    if len(paths) != 1:
        print(__doc__)
        return 2
    hits = found_markers(paths[0])
    if expect_present:
        missing = [m for m in [f"dex: {d}" for d in DEX_MARKERS] + [f"manifest: {m}" for m in MANIFEST_MARKERS] if m not in hits]
        if missing:
            print(f"{paths[0]}: expected markers are missing - the check would not notice them:")
            print("\n".join(f"  {m}" for m in missing))
            return 1
        print(f"{paths[0]}: all {len(hits)} markers present (the check can see them)")
        return 0
    if hits:
        print(f"{paths[0]}: contains code that belongs to the full flavor only:")
        print("\n".join(f"  {h}" for h in hits))
        return 1
    print(f"{paths[0]}: no online-access markers")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
