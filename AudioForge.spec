from pathlib import Path

from PyInstaller.utils.hooks import collect_all

project_root = Path(SPECPATH)
tkinterdnd_datas, tkinterdnd_binaries, tkinterdnd_hiddenimports = collect_all("tkinterdnd2")

analysis = Analysis(
    [str(project_root / "extract_audio.py")],
    pathex=[str(project_root)],
    binaries=[
        (str(project_root / "packaging" / "ffmpeg" / "ffmpeg.exe"), "."),
        *tkinterdnd_binaries,
    ],
    datas=[
        (str(project_root / "AudioForgeLogo.png"), "."),
        *tkinterdnd_datas,
    ],
    hiddenimports=tkinterdnd_hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)

pyz = PYZ(analysis.pure)
exe = EXE(
    pyz,
    analysis.scripts,
    analysis.binaries,
    analysis.datas,
    [],
    name="AudioForge",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
)
