$ErrorActionPreference = 'Stop'
$windowsRoot = Split-Path $PSScriptRoot -Parent
$project = Join-Path $windowsRoot 'src\NcmConverter.App\NcmConverter.App.csproj'
$publishDirectory = Join-Path $windowsRoot 'artifacts\exe\app'
$issFile = Join-Path $PSScriptRoot 'NcmConverter.iss'

if (Test-Path $publishDirectory) {
    Remove-Item -Recurse -Force $publishDirectory
}

Write-Host '正在发布 Windows 应用...'
dotnet publish $project -c Release -r win-x64 --self-contained true --nologo -o $publishDirectory
if ($LASTEXITCODE -ne 0) {
    throw 'dotnet publish 失败'
}

$isccCandidates = @(
    (Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe'),
    'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
    'C:\Program Files\Inno Setup 6\ISCC.exe'
)
$iscc = $isccCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) {
    throw '未找到 Inno Setup 6，请先安装：winget install JRSoftware.InnoSetup'
}

Write-Host "正在编译 EXE 安装包：$iscc"
& $iscc $issFile
if ($LASTEXITCODE -ne 0) {
    throw 'Inno Setup 编译失败'
}

$output = Join-Path $windowsRoot '..\dist\Windows\NcmConverter-0.1.0-setup.exe'
if (Test-Path $output) {
    $item = Get-Item $output
    Write-Host ("安装包已生成：{0}（{1:N1} MB）" -f $item.FullName, ($item.Length / 1MB))
} else {
    throw '没有找到生成的 EXE 安装包'
}
