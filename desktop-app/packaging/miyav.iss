; MIYAV desktop installer. Build the app image first:
;   gradle :desktop-app:packageAppImage
; then compile this script with Inno Setup (iscc desktop-app\packaging\miyav.iss).

#define AppName "MIYAV"
#define AppVersion "1.3.0"
#define AppImage "..\build\jpackage\MIYAV"

[Setup]
AppId={{6B0E8F2C-6E43-4C7B-9C55-3D7A1F0B2A91}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=MIYAV
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=..\build\installer
OutputBaseFilename=MIYAV-Setup-{#AppVersion}
SetupIconFile=miyav.ico
UninstallDisplayIcon={app}\MIYAV.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\MIYAV.exe"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\MIYAV.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\MIYAV.exe"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent
