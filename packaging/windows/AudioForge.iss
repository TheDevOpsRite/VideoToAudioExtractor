#define AppVersion GetEnv("APP_VERSION")

[Setup]
AppId={{B8C5FA41-48D6-4F30-AF69-AUDIOFORGE2026}
AppName=AudioForge
AppVersion={#AppVersion}
AppPublisher=The DevOps Rite
DefaultDirName={autopf}\AudioForge
DefaultGroupName=AudioForge
OutputDir=..\..\dist
OutputBaseFilename=AudioForge-Setup-{#AppVersion}
Compression=lzma
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\AudioForge.exe

[Files]
Source: "..\..\dist\AudioForge.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\AudioForgeLogo.png"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\AudioForge"; Filename: "{app}\AudioForge.exe"
Name: "{userdesktop}\AudioForge"; Filename: "{app}\AudioForge.exe"

[Run]
Filename: "{app}\AudioForge.exe"; Description: "Launch AudioForge"; Flags: nowait postinstall skipifsilent
