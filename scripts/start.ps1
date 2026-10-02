[CmdletBinding()]
param(
    [Parameter(Position = 0)][ValidateSet('backend', 'frontend', 'minio', 'ollama', 'all')][string]$Service = 'all',
    [int]$Port,
    [string]$Model,
    [int]$StartupTimeoutSeconds = 90,
    [switch]$SkipAI
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ConfigPath = Join-Path $ProjectRoot 'config/config.local.json'
function Stop-WithError([string]$Message) { Write-Error $Message; exit 1 }
function Test-Port([string]$HostName, [int]$TargetPort) {
    try { $addresses = [Net.Dns]::GetHostAddresses($HostName) } catch { return $false }
    foreach ($address in $addresses) {
        $client = [Net.Sockets.TcpClient]::new($address.AddressFamily)
        try {
            $attempt = $client.BeginConnect($address, $TargetPort, $null, $null)
            if ($attempt.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($attempt); return $true }
        } catch { } finally { $client.Dispose() }
    }
    return $false
}
function Wait-Port([string]$ServiceName, [string]$HostName, [int]$TargetPort) {
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) { if (Test-Port $HostName $TargetPort) { return }; Start-Sleep -Seconds 2 }
    Stop-WithError "$ServiceName did not become reachable at $HostName`:$TargetPort within $StartupTimeoutSeconds seconds."
}
function Wait-Http([string]$ServiceName, [string]$Url) {
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try { Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null; return } catch { Start-Sleep -Seconds 2 }
    }
    Stop-WithError "$ServiceName did not return HTTP health response at $Url within $StartupTimeoutSeconds seconds."
}
function Require-Path([string]$Path, [string]$Name) { if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) { Stop-WithError "$Name not found: $Path" } }
function Require-Secret([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match '^(YOUR_|CHANGE_ME|PLACEHOLDER)') { Stop-WithError "$Name is missing or still contains a placeholder in .env." }
}
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
	Require-Secret 'MINIO_ACCESS_KEY'; Require-Secret 'MINIO_SECRET_KEY'
	# Modern MinIO uses the ROOT names; the backend uses ACCESS/SECRET names.
	$env:MINIO_ROOT_USER = $env:MINIO_ACCESS_KEY
	$env:MINIO_ROOT_PASSWORD = $env:MINIO_SECRET_KEY
    if (Test-Port $Config.minio.host ([int]$Config.minio.apiPort)) { Write-Host "MinIO already running on $($Config.minio.host):$($Config.minio.apiPort)" }
    else {
    $exe = [string]$Config.paths.minioExecutable; $data = [string]$Config.paths.minioData; Require-Path $exe 'MinIO executable'; Require-Path $data 'MinIO data directory'
    Start-Process $exe -ArgumentList @('server', $data, '--address', ":$($Config.minio.apiPort)", '--console-address', ":$($Config.minio.consolePort)") | Out-Null; Write-Host "Starting MinIO: http://$($Config.minio.host):$($Config.minio.apiPort)"
    }
    Wait-Port 'MinIO' $Config.minio.host ([int]$Config.minio.apiPort)
    Wait-Http 'MinIO' "http://$($Config.minio.host):$($Config.minio.apiPort)/minio/health/live"
}
function Start-Ollama {
    $base = "http://$($Ollama.host):$ollamaPort"
    if (-not (Test-Port $Ollama.host $ollamaPort)) {
        if (-not(Get-Command ollama -ErrorAction SilentlyContinue)) { Stop-WithError 'Ollama command not found. Install Ollama and ensure it is on PATH.' }
        Start-Process ollama -ArgumentList serve -WindowStyle Hidden | Out-Null; Write-Host "Starting Ollama: $base"
    }
    else { Write-Host 'Ollama already running.' }
    Wait-Port 'Ollama' $Ollama.host $ollamaPort
    Wait-Http 'Ollama' "$base/api/tags"
    try { $tags = Invoke-RestMethod -Uri "$base/api/tags" -TimeoutSec 15 }catch { Stop-WithError "Ollama is not healthy at ${base}: $($_.Exception.Message)" }
    if ($tags.models.name -notcontains $ollamaModel) { Write-Warning "Configured Ollama model not found: $ollamaModel" }else { Write-Host "Model: $ollamaModel" }
}
function Confirm-CloudConfiguration {
    if ([string]::IsNullOrWhiteSpace($Cloud.baseUrl)) { Stop-WithError 'Cloud AI provider selected but AI_CLOUD_BASE_URL is missing.' }
    if ([string]::IsNullOrWhiteSpace($Cloud.model)) { Stop-WithError 'Cloud AI provider selected but AI_CLOUD_MODEL is missing.' }
    if ([string]::IsNullOrWhiteSpace($env:AI_CLOUD_API_KEY)) { Stop-WithError 'Cloud AI provider selected but AI_CLOUD_API_KEY is missing.' }
    Write-Host '[AI] Cloud configuration detected.'
}
function Start-Backend {
    if (Test-Port $Config.backend.host $backendPort) { Write-Host "Backend already running on $($Config.backend.host):$backendPort"; return }; Require-Path $backendDir 'Backend directory'; Require-Path (Join-Path $backendDir 'mvnw.cmd') 'Maven Wrapper'
    if (-not(Test-Port $Config.database.host ([int]$Config.database.port))) { Stop-WithError "PostgreSQL is not reachable at $($Config.database.host):$($Config.database.port). Check that PostgreSQL is running and accepting TCP connections." }
    Require-Secret 'DB_PASSWORD'; Require-Secret 'MINIO_ACCESS_KEY'; Require-Secret 'MINIO_SECRET_KEY'; Require-Secret 'JWT_SECRET'
    if ($env:DEMO_ADMIN_ENABLED -ne 'false' -and ([string]::IsNullOrWhiteSpace($env:DEMO_ADMIN_PASSWORD) -or $env:DEMO_ADMIN_PASSWORD -match '^(YOUR_|CHANGE_ME|PLACEHOLDER)')) {
        Write-Warning 'DEMO_ADMIN_PASSWORD is missing or still a placeholder. Set a real value before using the demo admin account.'
    }
    $env:SERVER_PORT = $backendPort; $env:DB_HOST = $Config.database.host; $env:DB_PORT = $Config.database.port; $env:DB_NAME = $Config.database.database; $env:DB_USERNAME = $Config.database.username; $env:MINIO_ENDPOINT = "http://$($Config.minio.host):$($Config.minio.apiPort)"; $env:MINIO_BUCKET = $Config.minio.bucket; $env:AI_PROVIDER = $AiProvider; $env:OLLAMA_BASE_URL = "http://$($Ollama.host):$ollamaPort"; $env:OLLAMA_MODEL = $ollamaModel; $env:AI_TIMEOUT = "$($Ollama.timeoutSeconds)s"; $env:AI_CLOUD_BASE_URL = $Cloud.baseUrl; $env:AI_CLOUD_MODEL = $Cloud.model; $env:AI_CLOUD_CHAT_COMPLETIONS_PATH = $Cloud.chatCompletionsPath; $env:AI_CLOUD_TIMEOUT = "$($Cloud.timeoutSeconds)s"
    Start-Window $backendDir '& .\mvnw.cmd spring-boot:run'; Write-Host "Starting backend: http://$($Config.backend.host):$backendPort"; Wait-Port 'Backend' $Config.backend.host $backendPort; Wait-Http 'Backend' "http://$($Config.backend.host):$backendPort/swagger-ui.html"
}
function Start-Frontend {
    if (Test-Port $Config.frontend.host $frontendPort) { Write-Host "Frontend already running on $($Config.frontend.host):$frontendPort"; return }; Require-Path $frontendDir 'Frontend directory'
    if (-not(Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules'))) { Stop-WithError "node_modules not found.`nRun: cd '$frontendDir'; npm install" }
    $env:VITE_API_BASE_URL = "http://$($Config.backend.host):$backendPort"; Start-Window $frontendDir "npm.cmd run dev -- --host $($Config.frontend.host) --port $frontendPort"; Write-Host "Starting frontend: http://$($Config.frontend.host):$frontendPort"; Wait-Port 'Frontend' $Config.frontend.host $frontendPort
}
if ($Service -eq 'all') { Write-Host "========================================`nDOAN_KLCN LOCAL ENVIRONMENT`n========================================"; Write-Host "[AI] Provider: $($AiProvider.ToUpperInvariant())"; Write-Host "`n[1/4] MinIO"; Start-Minio; Write-Host "`n[2/4] AI"; if ($SkipAI) { Write-Host '[AI] Startup skipped by -SkipAI.' } elseif ($AiProvider -eq 'ollama') { Start-Ollama } else { Write-Host '[AI] Ollama startup skipped.'; Confirm-CloudConfiguration }; Write-Host "`n[3/4] Backend"; Start-Backend; Write-Host "`n[4/4] Frontend"; Start-Frontend; Write-Host "`n========================================`nLOCAL ENVIRONMENT STARTED`n========================================" }
elseif ($Service -eq 'minio') { Start-Minio }elseif ($Service -eq 'ollama') { Start-Ollama }elseif ($Service -eq 'backend') { if ($AiProvider -eq 'cloud') { Confirm-CloudConfiguration }; Start-Backend }else { Start-Frontend }
