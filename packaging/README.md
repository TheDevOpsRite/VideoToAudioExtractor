# AudioForge packaging

The GitHub Actions workflow downloads the FFmpeg Windows runtime, builds `AudioForge.exe` with PyInstaller, and wraps it in an Inno Setup installer. The packaged app can also still use an FFmpeg executable found on `PATH` during local development.

Create a versioned GitHub Release by pushing a tag such as `v1.0.0`.
