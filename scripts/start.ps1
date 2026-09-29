[CmdletBinding()]
param(
    [Parameter(Position = 0)][ValidateSet('backend', 'frontend', 'minio', 'ollama', 'all')][string]$Service = 'all',
    [int]$Port,
    [string]$Model
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ConfigPath = Join-Path $ProjectRoot 'config/config.local.json'
function Stop-WithError([string]$Message) { Write-Error $Message; exit 1 }
function Test-Port([string]$HostName, [int]$TargetPort) {
    try { $c = [Net.Sockets.TcpClient]::new(); $a = $c.BeginConnect($HostName, $TargetPort, $null, $null); $ok = $a.AsyncWaitHandle.WaitOne(800); if ($ok) { $c.EndConnect($a) }; $c.Dispose(); $ok } catch { $false }
}
function Require-Path([string]$Path, [string]$Name) { if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) { Stop-WithError "$Name not found: $Path" } }
function Start-Window([string]$Dir, [string]$Command) { Start-Process powershell.exe -ArgumentList @('-NoExit', '-Command', "Set-Location -LiteralPath '$($Dir.Replace("'","''"))'; $Command") -WorkingDirectory $Dir | Out-Null }
if (-not (Test-Path -LiteralPath $ConfigPath)) { Stop-WithError "Local config not found: $ConfigPath. Copy config/config.example.json to config/config.local.json and complete it." }
try { $Config = Get-Content -Raw -LiteralPath $ConfigPath | ConvertFrom-Json }catch {
    Stop-WithError "Invalid JSON in ${ConfigPath}: $($_.Exception.Message)"
}
foreach ($name in 'paths', 'backend', 'frontend', 'database', 'minio', 'ai') { if ($null -eq $Config.$name) { Stop-WithError "Missing '$name' in $ConfigPath" } }
foreach ($name in 'provider', 'ollama', 'cloud') { if ($null -eq $Config.ai.$name) { Stop-WithError "Missing 'ai.$name' in $ConfigPath" } }
$EnvPath = Join-Path $ProjectRoot '.env'
if (Test-Path -LiteralPath $EnvPath) {
    foreach ($line in Get-Content -LiteralPath $EnvPath) {
        if ($line -match '^\s*([^#\s=]+)\s*=\s*(.*)\s*$') { Set-Item -Path "Env:$($matches[1])" -Value $matches[2] }
    }
}
$backendPort = if ($Service -eq 'backend' -and $PSBoundParameters.ContainsKey('Port')) { $Port }else { [int]$Config.backend.port }
$frontendPort = if ($Service -eq 'frontend' -and $PSBoundParameters.ContainsKey('Port')) { $Port }else { [int]$Config.frontend.port }
$AiProvider = ([string]$Config.ai.provider).Trim().ToLowerInvariant()
if ($AiProvider -notin @('ollama', 'cloud')) { Stop-WithError "Unsupported AI provider '$($Config.ai.provider)'. Use 'ollama' or 'cloud'." }
$Ollama = $Config.ai.ollama; $Cloud = $Config.ai.cloud
$ollamaPort = if ($Service -eq 'ollama' -and $PSBoundParameters.ContainsKey('Port')) { $Port }else { [int]$Ollama.port }
$ollamaModel = if ($Service -eq 'ollama' -and $PSBoundParameters.ContainsKey('Model')) { $Model }else { [string]$Ollama.model }
$backendDir = Join-Path $ProjectRoot $Config.paths.backend; $frontendDir = Join-Path $ProjectRoot $Config.paths.frontend
function Start-Minio {
    if (Test-Port $Config.minio.host ([int]$Config.minio.apiPort)) { Write-Host "Already running on port $($Config.minio.apiPort)"; return }
    $exe = [string]$Config.paths.minioExecutable; $data = [string]$Config.paths.minioData; Require-Path $exe 'MinIO executable'; Require-Path $data 'MinIO data directory'
    Start-Process $exe -ArgumentList @('server', $data, '--address', ":$($Config.minio.apiPort)", '--console-address', ":$($Config.minio.consolePort)") | Out-Null; Write-Host "Starting MinIO: http://$($Config.minio.host):$($Config.minio.apiPort)"
}
function Start-Ollama {
    $base = "http://$($Ollama.host):$ollamaPort"
    if (-not (Test-Port $Ollama.host $ollamaPort)) {
        if (-not(Get-Command ollama -ErrorAction SilentlyContinue)) { Stop-WithError 'Ollama command not found. Install Ollama and ensure it is on PATH.' }
        Start-Process ollama -ArgumentList serve -WindowStyle Hidden | Out-Null; Start-Sleep -Seconds 2
        if (-not(Test-Port $Ollama.host $ollamaPort)) { Stop-WithError "Ollama did not become reachable at $base" }; Write-Host "Started Ollama on port $ollamaPort"
    }
    else { Write-Host 'Ollama already running.' }
    try { $tags = Invoke-RestMethod -Uri "$base/api/tags" -TimeoutSec 5 }catch { Stop-WithError "Ollama is not healthy at ${base}: $($_.Exception.Message)" }
    if ($tags.models.name -notcontains $ollamaModel) { Write-Warning "Configured Ollama model not found: $ollamaModel" }else { Write-Host "Model: $ollamaModel" }
}
function Confirm-CloudConfiguration {
    if ([string]::IsNullOrWhiteSpace($Cloud.baseUrl)) { Stop-WithError 'Cloud AI provider selected but AI_CLOUD_BASE_URL is missing.' }
    if ([string]::IsNullOrWhiteSpace($Cloud.model)) { Stop-WithError 'Cloud AI provider selected but AI_CLOUD_MODEL is missing.' }
    if ([string]::IsNullOrWhiteSpace($env:AI_CLOUD_API_KEY)) { Stop-WithError 'Cloud AI provider selected but AI_CLOUD_API_KEY is missing.' }
    Write-Host '[AI] Cloud configuration detected.'
}
function Start-Backend {
    if (Test-Port $Config.backend.host $backendPort) { Write-Host "Already running on port $backendPort"; return }; Require-Path $backendDir 'Backend directory'; Require-Path (Join-Path $backendDir 'mvnw.cmd') 'Maven Wrapper'
    if (-not(Test-Port $Config.database.host ([int]$Config.database.port))) { Stop-WithError "PostgreSQL is not reachable at $($Config.database.host):$($Config.database.port). Check that PostgreSQL is running and accepting TCP connections." }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { Stop-WithError 'DB_PASSWORD is not set. Add it to the local .env file or current PowerShell session.' }
    if ([string]::IsNullOrWhiteSpace($env:MINIO_ACCESS_KEY) -or [string]::IsNullOrWhiteSpace($env:MINIO_SECRET_KEY)) { Stop-WithError 'MINIO_ACCESS_KEY and MINIO_SECRET_KEY must be set before starting the backend.' }
    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) { Stop-WithError 'JWT_SECRET is not set. Add it to the local .env file or current PowerShell session.' }
    if ([string]::IsNullOrWhiteSpace($env:DEMO_ADMIN_PASSWORD)) { Stop-WithError 'DEMO_ADMIN_PASSWORD is not set. Add it to .env, or set DEMO_ADMIN_ENABLED=false before starting the backend.' }
    $env:SERVER_PORT = $backendPort; $env:DB_HOST = $Config.database.host; $env:DB_PORT = $Config.database.port; $env:DB_NAME = $Config.database.database; $env:DB_USERNAME = $Config.database.username; $env:MINIO_ENDPOINT = "http://$($Config.minio.host):$($Config.minio.apiPort)"; $env:MINIO_BUCKET = $Config.minio.bucket; $env:AI_PROVIDER = $AiProvider; $env:OLLAMA_BASE_URL = "http://$($Ollama.host):$ollamaPort"; $env:OLLAMA_MODEL = $ollamaModel; $env:AI_TIMEOUT = "$($Ollama.timeoutSeconds)s"; $env:AI_CLOUD_BASE_URL = $Cloud.baseUrl; $env:AI_CLOUD_MODEL = $Cloud.model; $env:AI_CLOUD_CHAT_COMPLETIONS_PATH = $Cloud.chatCompletionsPath; $env:AI_CLOUD_TIMEOUT = "$($Cloud.timeoutSeconds)s"
    Start-Window $backendDir '& .\mvnw.cmd spring-boot:run'; Write-Host "Starting backend: http://$($Config.backend.host):$backendPort"
}
function Start-Frontend {
    if (Test-Port $Config.frontend.host $frontendPort) { Write-Host "Already running on port $frontendPort"; return }; Require-Path $frontendDir 'Frontend directory'
    if (-not(Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules'))) { Stop-WithError "node_modules not found.`nRun: cd '$frontendDir'; npm install" }
    $env:VITE_API_BASE_URL = "http://$($Config.backend.host):$backendPort"; Start-Window $frontendDir "npm run dev -- --host $($Config.frontend.host) --port $frontendPort"; Write-Host "Starting frontend: http://$($Config.frontend.host):$frontendPort"
}
if ($Service -eq 'all') { Write-Host "========================================`nDOAN_KLCN LOCAL ENVIRONMENT`n========================================"; Write-Host "[AI] Provider: $($AiProvider.ToUpperInvariant())"; Write-Host "`n[1/4] MinIO"; Start-Minio; Write-Host "`n[2/4] Ollama"; if ($AiProvider -eq 'ollama') { Start-Ollama } else { Write-Host '[AI] Ollama startup skipped.'; Confirm-CloudConfiguration }; Write-Host "`n[3/4] Backend"; Start-Backend; Write-Host "`n[4/4] Frontend"; Start-Frontend; Write-Host "`n========================================`nLOCAL ENVIRONMENT STARTED`n========================================" }
elseif ($Service -eq 'minio') { Start-Minio }elseif ($Service -eq 'ollama') { Start-Ollama }elseif ($Service -eq 'backend') { if ($AiProvider -eq 'cloud') { Confirm-CloudConfiguration }; Start-Backend }else { Start-Frontend }
