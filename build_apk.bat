@echo off
setlocal

echo Building U Type debug APK and exporting to apk\LankaKeys.apk...
if exist gradlew.bat (
    call gradlew.bat :app:exportApk
) else (
    call gradle :app:exportApk
)

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)

echo Done! APK is ready at: apk\LankaKeys.apk
endlocal
