# Gera o APK de debug usando somente JDK, SDK Android e Gradle Wrapper.
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$jdk = Get-ChildItem -LiteralPath 'C:\Program Files\Java' -Directory |
    Where-Object { $_.Name -match '^jdk-(17|21)(\.|$)' } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { throw 'Instale JDK 17 ou 21 em C:\Program Files\Java.' }
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties'))) {
    throw 'Falta mobile/local.properties com o caminho sdk.dir do SDK Android.'
}

$env:JAVA_HOME = $jdk.FullName
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user'
$env:ANDROID_SDK_HOME = $projectRoot
$env:JAVA_TOOL_OPTIONS = '-Duser.home="' + $projectRoot + '"'

$signingDir = Join-Path $projectRoot '.android'
$signingKey = Join-Path $signingDir 'debug.keystore'
New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
if (-not (Test-Path -LiteralPath $signingKey)) {
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $signingKey `
        -storepass android -alias androiddebugkey -keypass android `
        -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -validity 10000
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar a chave local de debug.' }
}

Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') testDebugUnitTest assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação. Veja a mensagem acima.' }
    Write-Host "APK pronto: $(Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk')"
} finally {
    Pop-Location
}
