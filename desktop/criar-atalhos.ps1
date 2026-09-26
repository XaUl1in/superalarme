$ErrorActionPreference = 'Stop'
$desktopDir = $PSScriptRoot
$projectDir = Split-Path -Parent $desktopDir
$iconPath = Join-Path $desktopDir 'assets\hardcore.ico'
if (-not (Test-Path -LiteralPath $iconPath)) {
    throw 'O ícone ainda não foi gerado. Execute python tools/generate_icon.py na pasta do projeto.'
}
$shell = New-Object -ComObject WScript.Shell
foreach ($item in @(
    @{ Name = 'Alarme Hardcore.lnk'; Target = 'abrir_alarme.bat'; Description = 'Abrir desafio e PIN do Alarme Hardcore' },
    @{ Name = 'Configurar Alarme Hardcore.lnk'; Target = 'configurar.bat'; Description = 'Parear o Alarme Hardcore com o celular' }
)) {
    $shortcut = $shell.CreateShortcut((Join-Path $projectDir $item.Name))
    $shortcut.TargetPath = Join-Path $desktopDir $item.Target
    $shortcut.WorkingDirectory = $desktopDir
    $shortcut.IconLocation = $iconPath + ',0'
    $shortcut.Description = $item.Description
    $shortcut.Save()
    Write-Host ('Criado: ' + (Join-Path $projectDir $item.Name))
}
