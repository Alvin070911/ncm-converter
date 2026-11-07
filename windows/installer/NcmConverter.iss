#define MyAppName "NCM 转 FLAC"
#define MyAppVersion "0.1.0"
#define MyAppPublisher "NcmConverter"
#define MyAppExeName "NcmConverter.App.exe"
#define MyAppSource "..\artifacts\exe\app"
#define MyAppIcon "..\src\NcmConverter.App\Assets\app.ico"

[Setup]
AppId={{7E7C3B0A-2A4E-4C1B-9A8F-2C5D6E7F8A9B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\NcmConverter
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
DisableDirPage=no
PrivilegesRequired=lowest
OutputDir=..\..\dist\Windows
OutputBaseFilename=NcmConverter-0.1.0-setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
SetupIconFile={#MyAppIcon}
UninstallDisplayIcon={app}\{#MyAppExeName}
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "chinesesimplified"; MessagesFile: "ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加图标："; Flags: unchecked

[Files]
Source: "{#MyAppSource}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "启动 {#MyAppName}"; Flags: nowait postinstall skipifsilent
[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  FindRec: TFindRec;
  NameLower: String;
begin
  if CurStep = ssInstall then
  begin
    if FindFirst(ExpandConstant('{app}\*'), FindRec) then
    begin
      try
        repeat
          if (FindRec.Attributes and FILE_ATTRIBUTE_DIRECTORY) <> 0 then
          begin
            NameLower := Lowercase(FindRec.Name);
            if (Pos('-', NameLower) > 0) and (NameLower <> 'zh-cn') and (NameLower <> 'en-us') then
            begin
              DelTree(ExpandConstant('{app}\') + FindRec.Name, True, True, True);
            end;
          end;
        until not FindNext(FindRec);
      finally
        FindClose(FindRec);
      end;
    end;
  end;
end;