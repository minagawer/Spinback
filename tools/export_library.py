#!/usr/bin/env python3
"""Export an old iTunes library into a folder that Spinback can read.

Output layout (pushed as-is to the app's external files dir):
  <out>/library.json
  <out>/music/<persistent id>.<ext>
  <out>/artwork/<artwork id>.jpg

Paths come from (first match wins): command-line options, environment variables
(SPINBACK_ITUNES_DIR / SPINBACK_EXPORT_DIR), tools/spinback.conf, then defaults
(~/Music/iTunes and ~/Spinback-export).

Regular files are hard-linked (no extra disk space). Android has no ALAC
decoder, so Apple Lossless files are converted with ffmpeg (--lossless):
  flac  bit-identical audio (default)
  aac   256kbps AAC via AudioToolbox, about 1/4 the size

Most album art lives in iTunes' "Album Artwork" cache rather than in the audio
files. It's extracted per album, using the track -> artwork mapping stored in
the (encrypted) iTunes Library.itl. Needs `cryptography` and Pillow; without
them the app falls back to art embedded in the audio files.
"""

import argparse
import io
import json
import os
import plistlib
import re
import shutil
import struct
import subprocess
import sys
import unicodedata
import urllib.parse
import zlib
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

CONFIG_FILE = Path(__file__).with_name("spinback.conf")
DEFAULT_ITUNES_DIR = "~/Music/iTunes"
DEFAULT_EXPORT_DIR = "~/Spinback-export"
LIBRARY_XML_NAMES = ("iTunes Library.xml", "iTunes Music Library.xml")
AUDIO_EXTS = {".m4a", ".mp3"}
MP4_CONTAINER_BOXES = {b"moov", b"trak", b"mdia", b"minf", b"stbl"}
VIDEO_KEYS = ("Has Video", "Movie", "Music Video", "TV Show")

# --lossless format -> (extension, ffmpeg codec/muxer args)
LOSSLESS_FORMATS = {
    "flac": (".flac", ["-c:a", "flac", "-f", "flac"]),
    "aac": (".m4a", ["-c:a", "aac_at", "-b:a", "256k", "-f", "ipod"]),
}


def read_config() -> dict[str, str]:
    """KEY=VALUE lines from tools/spinback.conf (the same file push_to_device.sh sources)."""
    config = {}
    if CONFIG_FILE.exists():
        for line in CONFIG_FILE.read_text(encoding="utf-8").splitlines():
            key, sep, value = line.strip().partition("=")
            if sep and not key.startswith("#"):
                config[key.strip()] = value.strip().strip("\"'")
    return config


def setting(cli_value: Path | None, name: str, config: dict[str, str], default: str) -> Path:
    value = cli_value or os.environ.get(name) or config.get(name) or default
    return Path(os.path.expanduser(os.path.expandvars(str(value))))


def find_library_xml(itunes: Path) -> Path:
    for name in LIBRARY_XML_NAMES:
        if (itunes / name).exists():
            return itunes / name
    sys.exit(
        f"No {' or '.join(LIBRARY_XML_NAMES)} in {itunes}.\n"
        "Set SPINBACK_ITUNES_DIR in tools/spinback.conf, or pass --itunes. If the XML is missing,\n"
        "enable \"Share iTunes Library XML with other applications\" in iTunes and open it once."
    )


def existing_path(path: str) -> Path | None:
    # macOS stores names in NFD, while the XML may use NFC.
    for form in ("NFC", "NFD"):
        candidate = Path(unicodedata.normalize(form, path))
        if candidate.exists():
            return candidate
    return None


def resolve_location(location: str, music_folder_url: str, itunes: Path) -> Path | None:
    """Local path of a track. Works even if the library was moved from another disk or Mac."""
    if not location.startswith("file://"):
        return None
    if location.startswith(music_folder_url):
        # Re-root under the iTunes folder: ".../iTunes Music/" or ".../iTunes Media/" + the rest.
        music_folder = Path(urllib.parse.unquote(urllib.parse.urlparse(music_folder_url).path))
        rel = urllib.parse.unquote(location[len(music_folder_url):])
        if found := existing_path(str(itunes / music_folder.name / rel)):
            return found
    return existing_path(urllib.parse.unquote(urllib.parse.urlparse(location).path))


def mp4_codecs(path: Path) -> set[bytes]:
    """Sample entry fourccs (b"alac", b"mp4a", ...) of an MP4 file. Reads only the box headers."""
    def boxes(f, start: int, end: int):
        pos = start
        while pos + 8 <= end:
            f.seek(pos)
            header = f.read(16)
            if len(header) < 8:
                return
            size, kind = struct.unpack(">I4s", header[:8])
            header_len = 8
            if size == 1:
                size, header_len = struct.unpack(">Q", header[8:16])[0], 16
            elif size == 0:
                size = end - pos
            if size < header_len:
                return
            yield kind, pos + header_len, pos + size
            pos += size

    def codecs(f, start: int, end: int):
        for kind, body, stop in boxes(f, start, end):
            if kind in MP4_CONTAINER_BOXES:
                yield from codecs(f, body, stop)
            elif kind == b"stsd":
                f.seek(body + 8)  # version, flags, entry count
                entry = f.read(8)
                if len(entry) == 8:
                    yield entry[4:8]

    with open(path, "rb") as f:
        return set(codecs(f, 0, f.seek(0, 2)))


def epoch_ms(value) -> int | None:
    if not isinstance(value, datetime):
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return int(value.timestamp() * 1000)


ITL_KEY = b"BHUILuilfghuila3"
ARTWORK_MAX_PX = 600
JPEG_FORMAT = b"\x00\x00\x00\x0d"


def read_itl(path: Path) -> bytes:
    """Decrypt and inflate an iTunes 10+ .itl library (AES-128-ECB over the first 100KB, then zlib)."""
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

    data = path.read_bytes()
    body = data[struct.unpack_from(">I", data, 4)[0]:]
    n = min(len(body), 102400) // 16 * 16
    decryptor = Cipher(algorithms.AES(ITL_KEY), modes.ECB()).decryptor()
    return zlib.decompress(decryptor.update(body[:n]) + decryptor.finalize() + body[n:])


def itl_artwork_ids(itl: bytes) -> dict[str, str]:
    """Track persistent id -> artwork id, from the little-endian "mith" track records."""
    ids = {}
    for m in re.finditer(b"mith", itl):
        o = m.start()
        # The header length check skips "mith" that is just text inside other records.
        if not 300 < struct.unpack_from("<I", itl, o + 4)[0] < 2000:
            continue
        track_id, artwork_id = struct.unpack_from("<Q", itl, o + 128)[0], struct.unpack_from("<Q", itl, o + 300)[0]
        if artwork_id:
            ids[f"{track_id:016x}"] = f"{artwork_id:016x}"
    return ids


def largest_itc_image(path: Path) -> tuple[int, int, bytes, bytes] | None:
    """(width, height, format, payload) of the biggest image in an .itc/.itc2 file."""
    data = path.read_bytes()
    offset = struct.unpack_from(">I", data, 0)[0]
    best = None
    while offset + 0xD0 <= len(data) and data[offset + 4:offset + 8] == b"item":
        size, header = struct.unpack_from(">I", data, offset)[0], struct.unpack_from(">I", data, offset + 8)[0]
        width, height = struct.unpack_from(">II", data, offset + 0x38)
        if best is None or width > best[0]:
            best = (width, height, data[offset + 0x30:offset + 0x34], data[offset + header:offset + size])
        offset += size
    return best


def save_artwork(paths: list[Path], dest: Path) -> None:
    from PIL import Image

    images = [img for img in map(largest_itc_image, paths) if img]
    width, height, fmt, payload = max(images, key=lambda img: img[0])
    if fmt == JPEG_FORMAT and max(width, height) <= ARTWORK_MAX_PX:
        dest.write_bytes(payload)
        return
    if fmt == b"ARGb":
        image = Image.frombuffer("RGBA", (width, height), payload, "raw", "ARGB", len(payload) // height, 1)
    else:
        image = Image.open(io.BytesIO(payload))
    image = image.convert("RGB")
    image.thumbnail((ARTWORK_MAX_PX, ARTWORK_MAX_PX))
    image.save(dest, "JPEG", quality=90)


def album_key(track: dict) -> tuple:
    """Same grouping as the app's Library.albums."""
    artist = track["artist"] or "不明なアーティスト"
    album_artist = track["albumArtist"] or ("Various Artists" if track["compilation"] else artist)
    return album_artist, track["album"] or "不明なアルバム"


def export_artwork(itunes: Path, tracks: list[dict], artwork_out: Path) -> int:
    """Set track["artwork"] from the iTunes artwork cache. Returns the number of albums with art."""
    try:
        import PIL  # noqa: F401
    except ImportError:
        print("  artwork: skipped (Pillow is not installed)")
        return 0
    try:
        artwork_ids = itl_artwork_ids(read_itl(itunes / "iTunes Library.itl"))
    except Exception as e:  # missing `cryptography` or an unexpected .itl version
        print(f"  artwork: can't read iTunes Library.itl ({e}); using per-track cache only")
        artwork_ids = {}

    # Files are named <library id>-<id>.itc[2]; the id is a track id (Cache) or an artwork id (Download).
    itc_files: dict[str, list[Path]] = {}
    for path in (itunes / "Album Artwork").rglob("*.itc*"):
        itc_files.setdefault(path.stem.split("-")[-1].lower(), []).append(path)

    source_by_album: dict[tuple, str] = {}
    for t in tracks:
        for source in (t["id"], artwork_ids.get(t["id"])):
            if source in itc_files:
                source_by_album.setdefault(album_key(t), source)
                break

    for source in set(source_by_album.values()):
        dest = artwork_out / f"{source}.jpg"
        if not dest.exists():
            try:
                save_artwork(itc_files[source], dest)
            except Exception as e:
                print(f"  artwork: skipped {source} ({e})")
    for t in tracks:
        source = source_by_album.get(album_key(t))
        t["artwork"] = f"artwork/{source}.jpg" if source and (artwork_out / f"{source}.jpg").exists() else None

    wanted = {f"{source}.jpg" for source in source_by_album.values()}
    for stale in artwork_out.iterdir():
        if stale.name not in wanted:
            stale.unlink()
    return sum(1 for source in source_by_album.values() if (artwork_out / f"{source}.jpg").exists())


def place_file(src: Path, dest: Path, convert_args: list[str] | None) -> str:
    if dest.exists():
        return "kept"
    tmp = dest.with_suffix(dest.suffix + ".tmp")
    if convert_args:
        subprocess.run(
            [
                "ffmpeg", "-nostdin", "-loglevel", "error", "-i", str(src),
                "-map", "0:a", "-map", "0:v?", "-c:v", "copy", "-disposition:v", "attached_pic",
                *convert_args, str(tmp),
            ],
            check=True,
            capture_output=True,
        )
        tmp.rename(dest)
        return "converted"
    try:
        os.link(src, dest)
        return "linked"
    except OSError:
        shutil.copy2(src, tmp)
        tmp.rename(dest)
        return "copied"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--itunes", type=Path, help=f"iTunes folder (default: {DEFAULT_ITUNES_DIR})")
    parser.add_argument("--out", type=Path, help=f"export folder (default: {DEFAULT_EXPORT_DIR})")
    parser.add_argument("--include-podcasts", action="store_true")
    parser.add_argument(
        "--lossless", choices=LOSSLESS_FORMATS, default="flac", help="format for Apple Lossless tracks"
    )
    parser.add_argument("--print-export-dir", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()

    config = read_config()
    args.itunes = setting(args.itunes, "SPINBACK_ITUNES_DIR", config, DEFAULT_ITUNES_DIR)
    args.out = setting(args.out, "SPINBACK_EXPORT_DIR", config, DEFAULT_EXPORT_DIR)
    if args.print_export_dir:
        print(args.out)
        return 0

    with open(find_library_xml(args.itunes), "rb") as f:
        lib = plistlib.load(f)

    music_folder_url = lib.get("Music Folder", "")
    music_out = args.out / "music"
    music_out.mkdir(parents=True, exist_ok=True)
    artwork_out = args.out / "artwork"
    artwork_out.mkdir(exist_ok=True)
    lossless_ext, lossless_args = LOSSLESS_FORMATS[args.lossless]

    tracks = []
    jobs = []  # (src, dest, ffmpeg args or None to link)
    skipped = 0
    for t in lib["Tracks"].values():
        if any(t.get(k) for k in VIDEO_KEYS) or (t.get("Podcast") and not args.include_podcasts):
            skipped += 1
            continue
        src = resolve_location(t.get("Location", ""), music_folder_url, args.itunes)
        if src is None or src.suffix.lower() not in AUDIO_EXTS:
            skipped += 1
            continue

        track_id = t["Persistent ID"].lower()
        lossless = src.suffix.lower() == ".m4a" and b"alac" in mp4_codecs(src)
        file_name = track_id + (lossless_ext if lossless else src.suffix.lower())
        jobs.append((src, music_out / file_name, lossless_args if lossless else None))
        tracks.append({
            "id": track_id,
            "name": t.get("Name") or src.stem,
            "artist": t.get("Artist"),
            "albumArtist": t.get("Album Artist"),
            "album": t.get("Album"),
            # iTunes' "sort as" overrides, which the iPod sorts by.
            "sortName": t.get("Sort Name"),
            "sortArtist": t.get("Sort Artist"),
            "sortAlbum": t.get("Sort Album"),
            "genre": t.get("Genre"),
            "year": t.get("Year"),
            "trackNumber": t.get("Track Number"),
            "discNumber": t.get("Disc Number"),
            "durationMs": t.get("Total Time"),
            "playCount": t.get("Play Count", 0),
            "lastPlayed": epoch_ms(t.get("Play Date UTC")),
            "dateAdded": epoch_ms(t.get("Date Added")),
            "compilation": bool(t.get("Compilation")),
            "file": f"music/{file_name}",
        })

    by_track_number = {str(t["Track ID"]): t["Persistent ID"].lower() for t in lib["Tracks"].values()}
    exported_ids = {t["id"] for t in tracks}
    playlists = []
    for p in lib.get("Playlists", []):
        if p.get("Master") or p.get("Distinguished Kind") or p.get("Folder") or p.get("Visible") is False:
            continue
        ids = [by_track_number.get(str(item["Track ID"])) for item in p.get("Playlist Items", [])]
        ids = [i for i in ids if i in exported_ids]
        if ids:
            playlists.append({"name": p["Name"], "trackIds": ids})

    if any(convert for _, _, convert in jobs) and not shutil.which("ffmpeg"):
        sys.exit("Apple Lossless tracks need ffmpeg to convert (brew install ffmpeg).")

    counts: dict[str, int] = {}
    with ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
        for i, result in enumerate(pool.map(lambda job: place_file(*job), jobs), 1):
            counts[result] = counts.get(result, 0) + 1
            if i % 250 == 0 or i == len(jobs):
                print(f"\r  files: {i}/{len(jobs)}", end="", flush=True)
    print()

    wanted = {dest.name for _, dest, _ in jobs}
    for stale in music_out.iterdir():
        if stale.name not in wanted:
            stale.unlink()

    albums_with_art = export_artwork(args.itunes, tracks, artwork_out)

    with open(args.out / "library.json", "w", encoding="utf-8") as f:
        json.dump({"version": 1, "tracks": tracks, "playlists": playlists}, f, ensure_ascii=False)

    print(f"tracks: {len(tracks)} (skipped {skipped}), playlists: {len(playlists)}")
    print("files:", ", ".join(f"{k} {v}" for k, v in sorted(counts.items())))
    print(f"artwork: {albums_with_art}/{len({album_key(t) for t in tracks})} albums")
    print(f"export: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
