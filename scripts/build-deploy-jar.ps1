# 서버에 올릴 jar를 만든다. 서버에 환경변수를 넣을 수 없을 때 쓴다.
#
#   .env          : 내 컴퓨터(Docker DB)에서 실행할 때 쓰는 값. 이 스크립트는 읽지 않는다.
#   .env.deploy   : 배포 DB(Neon 등) 접속 정보. 형식은 .env와 같다. git에는 올라가지 않는다.
#
# 1) .env.deploy 로 src/main/resources/application-local.yml 을 만들고
# 2) gradlew clean bootJar 로 jar를 만든 뒤
# 3) application-local.yml 을 반드시 지운다. (남아 있으면 내 컴퓨터 실행이 배포 DB를 보게 됨)
#
# 사용:  powershell -ExecutionPolicy Bypass -File scripts/build-deploy-jar.ps1
# 결과:  build/libs/vibe-midterm-0.0.1-SNAPSHOT.jar  (접속 정보가 들어 있으니 GitHub에 올리지 말 것)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$generated = Join-Path $root 'src/main/resources/application-local.yml'

Set-Location $root
try {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'make-deploy-config.ps1')
    if ($LASTEXITCODE -ne 0) { throw '배포용 설정 생성에 실패했습니다.' }

    & (Join-Path $root 'gradlew.bat') clean bootJar
    if ($LASTEXITCODE -ne 0) { throw 'jar 빌드에 실패했습니다.' }

    Write-Output ''
    Write-Output 'jar를 만들었습니다: build/libs/vibe-midterm-0.0.1-SNAPSHOT.jar'
    Write-Output '이 jar에는 배포 DB 접속 정보가 들어 있습니다. 제출이 끝나면 DB 비밀번호를 바꾸세요.'
}
finally {
    if (Test-Path $generated) {
        Remove-Item $generated -Force
        Write-Output 'application-local.yml 을 지웠습니다. (로컬 실행은 .env를 사용합니다)'
    }
}
