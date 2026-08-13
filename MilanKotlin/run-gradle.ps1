# 统一 Gradle 运行入口（沙箱环境专用）：
# 默认的 %USERPROFILE%\.gradle 与 %USERPROFILE%\.android 在 DSH 文件沙箱下不可写，
# 因此把 GRADLE_USER_HOME / ANDROID_USER_HOME 重定向到 workspace 内目录。
# 用法：pwsh -File run-gradle.ps1 <gradle args...>（工作目录须为 MilanKotlin）
$env:GRADLE_USER_HOME = "C:\Users\Administrator\Downloads\work\milan\.gradle-home"
$env:ANDROID_USER_HOME = "C:\Users\Administrator\Downloads\work\milan\.android-home"
if (!(Test-Path $env:ANDROID_USER_HOME)) { New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null }
& ".\gradlew.bat" @args
exit $LASTEXITCODE
