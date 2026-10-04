"""Extract an audio track from a video file using FFmpeg."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path


FORMAT_SETTINGS = {
    "mp3": {"extension": ".mp3", "codec": "libmp3lame", "quality": ["-q:a", "2"]},
    "wav": {"extension": ".wav", "codec": "pcm_s16le", "quality": []},
    "m4a": {"extension": ".m4a", "codec": "aac", "quality": ["-b:a", "192k"]},
    "flac": {"extension": ".flac", "codec": "flac", "quality": []},
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract the audio stream from a video file using FFmpeg."
    )
    parser.add_argument("video", type=Path, help="Path to the input video file")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Output audio path; defaults to the video name with the selected extension",
    )
    parser.add_argument(
        "-f",
        "--format",
        choices=sorted(FORMAT_SETTINGS),
        default="mp3",
        help="Audio format (default: mp3)",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Replace the output file if it already exists",
    )
    return parser.parse_args()


def extract_audio(video: Path, output: Path, audio_format: str, overwrite: bool) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise RuntimeError(
            "FFmpeg was not found. Install FFmpeg and make sure its folder is in PATH."
        )

    if not video.is_file():
        raise FileNotFoundError(f"Input video does not exist: {video}")

    if output.exists() and not overwrite:
        raise FileExistsError(
            f"Output already exists: {output}. Use --overwrite to replace it."
        )

    output.parent.mkdir(parents=True, exist_ok=True)
    settings = FORMAT_SETTINGS[audio_format]
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(video),
        "-vn",
        "-map",
        "0:a:0",
        "-c:a",
        settings["codec"],
        *settings["quality"],
        "-y" if overwrite else "-n",
        str(output),
    ]

    try:
        subprocess.run(command, check=True)
    except subprocess.CalledProcessError as error:
        raise RuntimeError(
            "FFmpeg could not extract an audio stream. "
            "Check that the video contains audio."
        ) from error


def main() -> int:
    args = parse_args()
    settings = FORMAT_SETTINGS[args.format]
    output = args.output or args.video.with_suffix(settings["extension"])

    try:
        extract_audio(args.video, output, args.format, args.overwrite)
    except (FileNotFoundError, FileExistsError, RuntimeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    print(f"Audio extracted successfully: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
