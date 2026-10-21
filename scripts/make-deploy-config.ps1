# 서버에 환경변수를 넣을 수 없고 jar만 올릴 수 있을 때 쓰는 배포용 설정 생성 스크립트.
#
# 프로젝트 루트의 .env.deploy(배포 DB 접속 정보 등. 형식은 .env와 같음)를 읽어 src/main/resources/application-local.yml 을 만든다.
# 이 파일은 .gitignore 에 들어 있어 git에는 올라가지 않고, 이 파일이 있는 상태로 빌드한 jar에만 들어간다.
# (jar에 접속 정보가 들어가므로 jar를 다른 사람에게 주었다면 제출이 끝난 뒤 DB 비밀번호를 바꾸세요.)
#
# 보통은 직접 쓰지 말고 scripts/build-deploy-jar.ps1 로 한 번에 실행한다. (빌드 뒤 이 파일을 자동으로 지워서
# 내 컴퓨터에서 Docker DB로 실행할 때 배포 DB로 접속하는 일이 없게 한다)

param([string]$EnvFileName = '.env.deploy')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root $EnvFileName
$target = Join-Path $root 'src/main/resources/application-local.yml'

if (-not (Test-Path $envFile)) { throw "$EnvFileName 파일이 없습니다: $envFile (.env를 복사해 만들고 배포 DB 값으로 고치세요)" }

# .env 읽기 (UTF-8, 주석과 빈 줄 제외)
$values = @{}
foreach ($line in [System.IO.File]::ReadAllLines($envFile, [System.Text.Encoding]::UTF8)) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    $i = $t.IndexOf('=')
    if ($i -lt 1) { continue }
    $values[$t.Substring(0, $i).Trim()] = $t.Substring($i + 1).Trim().Trim('"').Trim("'")
}

function Quote([string]$s) { "'" + $s.Replace("'", "''") + "'" }
function Has([string]$k) { $values.ContainsKey($k) -and $values[$k] -ne '' }

if (-not (Has 'DB_URL')) { throw "$EnvFileName 에 DB_URL이 없습니다." }
if (-not (Has 'DB_USERNAME')) { throw "$EnvFileName 에 DB_USERNAME이 없습니다." }

$out = New-Object System.Collections.Generic.List[string]
$out.Add('# 자동 생성 파일 (scripts/make-deploy-config.ps1). git에는 올라가지 않는다.')
$out.Add('spring:')
$out.Add('  datasource:')
$out.Add('    url: ' + (Quote $values['DB_URL']))
$out.Add('    username: ' + (Quote $values['DB_USERNAME']))
$out.Add('    password: ' + (Quote ($(if (Has 'DB_PASSWORD') { $values['DB_PASSWORD'] } else { '' }))))
$out.Add('app:')
$out.Add('  instructor:')
foreach ($pair in @(@('INSTRUCTOR_EMAIL', 'email'), @('INSTRUCTOR_NAME', 'name'), @('INSTRUCTOR_PASSWORD', 'password'))) {
    if (Has $pair[0]) { $out.Add('    ' + $pair[1] + ': ' + (Quote $values[$pair[0]])) }
}
if (Has 'REMEMBER_ME_KEY') {
    $out.Add('  remember-me:')
    $out.Add('    key: ' + (Quote $values['REMEMBER_ME_KEY']))
}

[System.IO.File]::WriteAllLines($target, $out, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "생성했습니다: $target (DB_URL, 계정 값은 화면에 출력하지 않습니다)"
