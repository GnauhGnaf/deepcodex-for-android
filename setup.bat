@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================
echo   Android 项目环境初始化 (国内镜像版)
echo ============================================
echo.

set "SDK_ROOT=%USERPROFILE%\Android\Sdk"
set "CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
set "PROJECT_DIR=%~dp0"

:: === Check Java ===
echo [1/3] 检查 Java 环境 ...
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [FAIL] 未找到 Java！请安装 JDK 17+
    echo   下载: https://mirrors.tuna.tsinghua.edu.cn/Adoptium/
    pause
    exit /b 1
)
java -version 2>&1 | findstr /i "version"
echo   [OK] Java 可用
echo.

:: === Check Android SDK ===
echo [2/3] 检查 Android SDK ...
if exist "%SDK_ROOT%\platforms\android-34" (
    echo   [OK] Android SDK 已安装: %SDK_ROOT%
    goto :check_wrapper
)

echo   未找到 Android SDK，开始自动安装...
echo   下载 cmdline-tools...

set "ZIP_PATH=%TEMP%\cmdline-tools.zip"
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%CMDLINE_TOOLS_URL%' -OutFile '%ZIP_PATH%';}" 2>nul

if not exist "%ZIP_PATH%" (
    echo   [FAIL] 下载 SDK 失败，请手动下载并解压到 %SDK_ROOT%
    echo   下载地址: %CMDLINE_TOOLS_URL%
    pause
    exit /b 1
)

echo   解压并安装 SDK 组件...
mkdir "%SDK_ROOT%\cmdline-tools\latest" 2>nul
powershell -Command "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%TEMP%\sdk-tmp' -Force" 2>nul
xcopy /E /Y "%TEMP%\sdk-tmp\cmdline-tools\*" "%SDK_ROOT%\cmdline-tools\latest\" >nul 2>&1
rmdir /S /Q "%TEMP%\sdk-tmp" 2>nul
del "%ZIP_PATH%" 2>nul

echo   安装 SDK 组件 (platforms, build-tools, platform-tools) ...
call "%SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK_ROOT%" "platform-tools" "build-tools;34.0.0" "platforms;android-34"

if exist "%SDK_ROOT%\platforms\android-34" (
    echo   [OK] Android SDK 安装完成
) else (
    echo   [FAIL] 自动安装失败，请手动安装 Android Studio
    pause
    exit /b 1
)

:: === Write local.properties ===
echo sdk.dir=%SDK_ROOT:\=/%> "%PROJECT_DIR%local.properties"
echo.

:check_wrapper
:: === Build ===
echo [3/3] 开始构建项目...
echo.
cd /d "%PROJECT_DIR%"
call gradlew.bat assembleDebug

if %ERRORLEVEL% equ 0 (
    echo.
    echo ============================================
    echo   构建成功！
    echo   APK 位置: %PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk
    echo ============================================
) else (
    echo.
    echo 构建失败，请检查上方错误信息。
)

echo.
pause
