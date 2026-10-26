# One-time: creates the key every Kiwi Calendar build will be signed with, and
# prints the four repository secrets the Android workflow reads.
#
#   powershell -ExecutionPolicy Bypass -File scripts\make-signing-key.ps1
#
# Uses Java's keytool if it's installed, otherwise Docker Desktop.
# The key is saved in your user folder, outside the repository, so it can't be
# committed by accident. Back it up: if you lose it, updates stop installing.

$ErrorActionPreference = "Stop"

$folder = Join-Path $HOME "kiwi-calendar-signing"
$keystoreName = "kiwi-calendar.jks"
$keystore = Join-Path $folder $keystoreName
$alias = "kiwicalendar"

if (Test-Path $keystore) {
    throw "$keystore already exists. Use that key, or move it away first if you really want a new one."
}
New-Item -ItemType Directory -Force -Path $folder | Out-Null

$bytes = New-Object byte[] 18
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = -join ($bytes | ForEach-Object { $_.ToString("x2") })

$keytoolArgs = @(
    "-genkeypair", "-keystore", $keystoreName, "-alias", $alias,
    "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-storetype", "PKCS12",
    "-storepass", $password, "-keypass", $password, "-dname", "CN=Kiwi Calendar"
)

if (Get-Command keytool -ErrorAction SilentlyContinue) {
    Push-Location $folder
    try { & keytool @keytoolArgs } finally { Pop-Location }
} elseif (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "keytool not found, using Docker (the first run downloads a Java image)..."
    & docker run --rm -v "${folder}:/work" -w /work eclipse-temurin:17-jdk keytool @keytoolArgs
} else {
    throw "This needs Java's keytool or Docker Desktop. Install either one and run it again."
}
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keystore)) {
    throw "keytool didn't create the key. See the output above."
}

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
$base64File = Join-Path $folder "keystore.b64"
[IO.File]::WriteAllText($base64File, $base64)
$copied = $true
try { Set-Clipboard -Value $base64 } catch { $copied = $false }

Write-Host ""
Write-Host "Created $keystore"
Write-Host ""
Write-Host "In GitHub, open your repo's Settings > Secrets and variables > Actions,"
Write-Host "and add these four repository secrets:"
Write-Host ""
if ($copied) {
    Write-Host "  KEYSTORE_BASE64    (copied to your clipboard; also saved in $base64File)"
} else {
    Write-Host "  KEYSTORE_BASE64    (the contents of $base64File)"
}
Write-Host "  KEYSTORE_PASSWORD  $password"
Write-Host "  KEY_ALIAS          $alias"
Write-Host "  KEY_PASSWORD       $password"
Write-Host ""
Write-Host "Then delete keystore.b64, and back up $keystoreName and the password somewhere safe,"
Write-Host "like a password manager. Never commit either one."
