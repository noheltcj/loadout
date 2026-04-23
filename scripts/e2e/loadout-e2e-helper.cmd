@echo off
setlocal

if "%~1"=="__printenv__" goto printenv

if not defined LOADOUT_E2E_BINARY_PATH (
    >&2 echo Loadout E2E helper requires LOADOUT_E2E_BINARY_PATH
    exit /b 1
)

"%LOADOUT_E2E_BINARY_PATH%" %*
exit /b %errorlevel%

:printenv
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0loadout-e2e-printenv.ps1" %*
exit /b %errorlevel%
