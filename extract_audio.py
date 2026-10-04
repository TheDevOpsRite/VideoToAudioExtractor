"""AudioForge: extract an audio track from a video file using FFmpeg."""

from __future__ import annotations

import argparse
import ctypes
import shutil
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from pathlib import Path

try:
    from tkinterdnd2 import DND_FILES, TkinterDnD
except ImportError:
    DND_FILES = None
    TkinterDnD = None


FORMAT_SETTINGS = {
    "mp3": {"extension": ".mp3", "codec": "libmp3lame", "quality": ["-q:a", "2"]},
    "wav": {"extension": ".wav", "codec": "pcm_s16le", "quality": []},
    "m4a": {"extension": ".m4a", "codec": "aac", "quality": ["-b:a", "192k"]},
    "flac": {"extension": ".flac", "codec": "flac", "quality": []},
}


def enable_windows_dpi_awareness() -> None:
    if sys.platform != "win32":
        return
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except (AttributeError, OSError):
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except (AttributeError, OSError):
            pass


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


class AudioForgeApp:
    """Small desktop interface for the existing FFmpeg extraction function."""

    COLORS = {
        "background": "#101418",
        "surface": "#171d22",
        "surface_light": "#202930",
        "border": "#34414a",
        "text": "#f4f7f8",
        "muted": "#9aa8b1",
        "accent": "#43d6a3",
        "accent_dark": "#259b76",
        "brand_blue": "#27b8f2",
        "danger": "#ff8b8b",
    }

    def __init__(self, root: tk.Misc) -> None:
        self.root = root
        self.root.title("AudioForge | Video to Audio")
        self.root.geometry("820x720")
        self.root.minsize(680, 620)
        self.root.configure(bg=self.COLORS["background"])
        self.root.option_add("*Font", ("Segoe UI", 10))
        self._set_window_icon()
        self.selected_video: Path | None = None
        self.is_converting = False
        self.format_var = tk.StringVar(value="mp3")
        self.status_var = tk.StringVar(value="Ready when you are")
        self.file_var = tk.StringVar(value="Drop a video here or browse your files")
        self._build_styles()
        self._build_ui()

    def _rounded_panel(self, canvas: tk.Canvas, width: int, height: int) -> None:
        radius = 8
        canvas.create_rectangle(
            radius, 0, width - radius, height,
            fill=self.COLORS["surface"], outline="", tags="panel",
        )
        canvas.create_rectangle(
            0, radius, width, height - radius,
            fill=self.COLORS["surface"], outline="", tags="panel",
        )
        for x, y, start in (
            (0, 0, 90),
            (width - radius * 2, 0, 0),
            (0, height - radius * 2, 180),
            (width - radius * 2, height - radius * 2, 270),
        ):
            canvas.create_arc(
                x, y, x + radius * 2, y + radius * 2,
                start=start, extent=90, fill=self.COLORS["surface"], outline="", tags="panel",
            )
        canvas.create_rectangle(
            radius, 1, width - radius, 1,
            fill=self.COLORS["border"], outline="", tags="border",
        )

    def _load_center_logo(self) -> tk.PhotoImage | None:
        logo_path = Path(__file__).with_name("AudioForgeLogo.png")
        if not logo_path.is_file():
            return None
        try:
            source = tk.PhotoImage(file=str(logo_path))
            crop_height = int(source.height() * 0.68)
            cropped = tk.PhotoImage()
            cropped.tk.call(
                cropped, "copy", source,
                "-from", 0, 0, source.width(), crop_height,
            )
            scale = max(1, source.width() // 170)
            return cropped.subsample(scale, scale)
        except tk.TclError:
            return None

    def _set_window_icon(self) -> None:
        icon_path = Path(__file__).with_name("AudioForgeLogo.png")
        if not icon_path.is_file():
            return
        try:
            self.window_icon = tk.PhotoImage(file=str(icon_path))
            self.root.iconphoto(True, self.window_icon)
        except tk.TclError:
            self.window_icon = None

    def _build_styles(self) -> None:
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure(
            "App.TFrame", background=self.COLORS["background"]
        )
        style.configure(
            "Panel.TFrame",
            background=self.COLORS["surface"],
            relief="flat",
        )
        style.configure(
            "App.TLabel",
            background=self.COLORS["background"],
            foreground=self.COLORS["text"],
        )
        style.configure(
            "Muted.TLabel",
            background=self.COLORS["background"],
            foreground=self.COLORS["muted"],
        )
        style.configure(
            "Panel.TLabel",
            background=self.COLORS["surface"],
            foreground=self.COLORS["text"],
        )
        style.configure(
            "Format.TCombobox",
            fieldbackground=self.COLORS["surface_light"],
            background=self.COLORS["surface_light"],
            foreground=self.COLORS["text"],
            selectbackground=self.COLORS["surface_light"],
            selectforeground=self.COLORS["text"],
            arrowcolor=self.COLORS["accent"],
            bordercolor=self.COLORS["border"],
            padding=(10, 7),
        )
        style.map(
            "Format.TCombobox",
            fieldbackground=[("readonly", self.COLORS["surface_light"])],
            foreground=[("readonly", self.COLORS["text"])],
            selectbackground=[("readonly", self.COLORS["surface_light"])],
            selectforeground=[("readonly", self.COLORS["text"])],
        )
        style.configure(
            "Audio.Horizontal.TProgressbar",
            troughcolor=self.COLORS["surface_light"],
            background="#22d3ee",
            bordercolor=self.COLORS["surface_light"],
            lightcolor="#22d3ee",
            darkcolor="#22d3ee",
            thickness=8,
        )
        self.root.option_add("*TCombobox*Listbox.background", self.COLORS["surface_light"])
        self.root.option_add("*TCombobox*Listbox.foreground", self.COLORS["text"])
        self.root.option_add("*TCombobox*Listbox.selectBackground", self.COLORS["accent_dark"])
        self.root.option_add("*TCombobox*Listbox.selectForeground", self.COLORS["text"])
        style.configure(
            "Convert.TButton",
            background=self.COLORS["accent"],
            foreground="#07130f",
            font=("Segoe UI", 11, "bold"),
            padding=(22, 12),
            borderwidth=0,
        )
        style.map(
            "Convert.TButton",
            background=[("active", self.COLORS["accent_dark"]), ("disabled", "#405049")],
            foreground=[("disabled", "#9aa8a0")],
        )
        style.configure(
            "Browse.TButton",
            background=self.COLORS["surface_light"],
            foreground=self.COLORS["text"],
            padding=(15, 9),
            borderwidth=1,
        )
        style.map("Browse.TButton", background=[("active", self.COLORS["border"])])

    def _build_ui(self) -> None:
        main = ttk.Frame(self.root, style="App.TFrame", padding=(64, 42, 64, 28))
        main.pack(fill="both", expand=True)

        self.center_logo = self._load_center_logo()
        if self.center_logo is not None:
            tk.Label(
                main,
                image=self.center_logo,
                bg=self.COLORS["background"],
                borderwidth=0,
            ).pack(pady=(0, 13))

        title = tk.Frame(main, bg=self.COLORS["background"])
        title.pack()
        tk.Label(
            title,
            text="Audio",
            bg=self.COLORS["background"],
            fg=self.COLORS["text"],
            font=("Segoe UI", 30, "bold"),
        ).pack(side="left")
        tk.Label(
            title,
            text="Forge",
            bg=self.COLORS["background"],
            fg=self.COLORS["brand_blue"],
            font=("Segoe UI", 30, "bold"),
        ).pack(side="left")
        ttk.Label(
            main,
            text="Turn video into sound, beautifully.",
            style="Muted.TLabel",
            font=("Segoe UI", 11),
        ).pack(pady=(4, 34))

        drop = tk.Canvas(
            main, height=205, bg=self.COLORS["background"], highlightthickness=0, cursor="hand2"
        )
        drop.pack(fill="x")
        drop.bind("<Configure>", lambda event: self._position_drop_content(drop, event.width))
        self._rounded_panel(drop, 700, 205)
        drop.bind("<Button-1>", lambda _event: self._browse())
        icon = tk.Canvas(drop, width=44, height=44, bg=self.COLORS["surface"], highlightthickness=0)
        icon.create_line(22, 5, 22, 31, fill=self.COLORS["accent"], width=3)
        icon.create_line(12, 22, 22, 32, fill=self.COLORS["accent"], width=3)
        icon.create_line(32, 22, 22, 32, fill=self.COLORS["accent"], width=3)
        icon.create_line(8, 38, 36, 38, fill=self.COLORS["border"], width=2)
        self.file_label = tk.Label(
            drop,
            textvariable=self.file_var, anchor="center",
            bg=self.COLORS["surface"],
            fg=self.COLORS["text"],
            font=("Segoe UI", 12, "bold"),
        )
        self.drop_hint = tk.Label(
            drop,
            text="MP4, MOV, AVI, MKV and more",
            bg=self.COLORS["surface"],
            fg=self.COLORS["muted"],
            font=("Segoe UI", 9),
        )
        self.drop_widgets = (icon, self.file_label, self.drop_hint)
        self._position_drop_content(drop, 700)

        if TkinterDnD is not None:
            drop.drop_target_register(DND_FILES)
            drop.dnd_bind("<<Drop>>", self._handle_drop)

        options = tk.Canvas(
            main, height=64, bg=self.COLORS["background"], highlightthickness=0
        )
        options.pack(fill="x", pady=(30, 0))
        options.bind("<Configure>", lambda event: self._position_options(options, event.width))
        self._rounded_panel(options, 700, 64)
        output_label = tk.Label(
            options,
            text="OUTPUT FORMAT",
            bg=self.COLORS["surface"],
            fg=self.COLORS["muted"],
            font=("Segoe UI", 9, "bold"),
        )
        self.format_box = ttk.Combobox(
            options,
            textvariable=self.format_var,
            values=("mp3", "wav", "m4a", "flac"),
            state="readonly",
            width=11,
            style="Format.TCombobox",
        )
        self.output_label = output_label
        self.options_canvas = options
        self._position_options(options, 700)

        self.convert_button = ttk.Button(
            main, text="Convert audio", command=self._convert, style="Convert.TButton"
        )
        self.convert_button.pack(fill="x", pady=(30, 15))
        self.progress = ttk.Progressbar(
            main, mode="indeterminate", style="Audio.Horizontal.TProgressbar"
        )
        self.progress.pack(fill="x")
        ttk.Label(main, textvariable=self.status_var, style="Muted.TLabel", font=("Segoe UI", 9)).pack(pady=(10, 0))
        ttk.Label(main, text="Created by The DevOps Rite", style="Muted.TLabel", font=("Segoe UI", 9)).pack(side="bottom", pady=(24, 0))

    def _position_drop_content(self, canvas: tk.Canvas, width: int) -> None:
        center = max(width, 250) // 2
        if not hasattr(self, "drop_windows"):
            self.drop_windows = [
                canvas.create_window(
                    center, 28 + index * 52, window=widget, anchor="n", tags=f"drop_{index}"
                )
                for index, widget in enumerate(self.drop_widgets)
            ]
        else:
            for index, window_id in enumerate(self.drop_windows):
                canvas.coords(window_id, center, 28 + index * 52)

    def _position_options(self, canvas: tk.Canvas, width: int) -> None:
        center_y = 32
        if not hasattr(self, "option_windows"):
            self.option_windows = [
                canvas.create_window(18, center_y, window=self.output_label, anchor="w", tags="output_label"),
                canvas.create_window(
                    max(width - 18, 140), center_y, window=self.format_box, anchor="e", tags="format_box"
                ),
            ]
        else:
            canvas.coords(self.option_windows[0], 18, center_y)
            canvas.coords(self.option_windows[1], max(width - 18, 140), center_y)

    def _browse(self) -> None:
        selected = filedialog.askopenfilename(
            title="Choose a video file",
            filetypes=[("Video files", "*.mp4 *.mov *.avi *.mkv *.webm *.flv *.wmv"), ("All files", "*.*")],
        )
        if selected:
            self._set_video(Path(selected))

    def _handle_drop(self, event: tk.Event) -> None:
        if DND_FILES is not None:
            paths = self.root.tk.splitlist(event.data)
            if paths:
                self._set_video(Path(paths[0]))

    def _set_video(self, video: Path) -> None:
        if not video.is_file():
            messagebox.showerror("Invalid file", "Please choose a video file that exists.")
            return
        self.selected_video = video
        self.file_var.set(video.name)
        self.status_var.set("Video ready to convert")

    def _convert(self) -> None:
        if self.is_converting:
            return
        if self.selected_video is None:
            messagebox.showinfo("Choose a video", "Drop a video into the panel or browse for one first.")
            return
        self.is_converting = True
        self.convert_button.configure(state="disabled")
        self.format_box.configure(state="disabled")
        self.progress.start(10)
        self.status_var.set("Extracting audio...")
        thread = threading.Thread(target=self._run_conversion, daemon=True)
        thread.start()

    def _run_conversion(self) -> None:
        assert self.selected_video is not None
        settings = FORMAT_SETTINGS[self.format_var.get()]
        output = Path.home() / "Downloads" / f"{self.selected_video.stem}{settings['extension']}"
        try:
            extract_audio(self.selected_video, output, self.format_var.get(), overwrite=True)
        except (FileNotFoundError, RuntimeError) as error:
            self.root.after(0, lambda: self._conversion_finished(str(error), False))
        else:
            self.root.after(0, lambda: self._conversion_finished(str(output), True))

    def _conversion_finished(self, result: str, success: bool) -> None:
        self.is_converting = False
        self.progress.stop()
        self.convert_button.configure(state="normal")
        self.format_box.configure(state="readonly")
        if success:
            self.status_var.set("Conversion complete")
            messagebox.showinfo("AudioForge", f"Your audio was saved to:\n{result}")
        else:
            self.status_var.set("Conversion could not be completed")
            messagebox.showerror("Conversion failed", result)


def launch_gui() -> int:
    enable_windows_dpi_awareness()
    root_class = TkinterDnD.Tk if TkinterDnD is not None else tk.Tk
    root = root_class()
    AudioForgeApp(root)
    root.mainloop()
    return 0


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
    if len(sys.argv) == 1:
        raise SystemExit(launch_gui())
    raise SystemExit(main())
