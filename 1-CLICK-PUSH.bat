@echo off
title CineStream - Push to GitHub
color 0B
cls
echo ===================================================
echo   CineStream Player - Push to GitHub
echo   Repository: JUNAIADSRK555/cine-stream-gallery-player
echo ===================================================
echo.
git push -u origin main
echo.
if %ERRORLEVEL% EQU 0 (
    echo ===================================================
    echo  [SUCCESS] Code successfully pushed to GitHub!
    echo ===================================================
) else (
    echo ===================================================
    echo  [NOTE] If GitHub rejects .github/workflows:
    echo  Please ensure your GitHub Personal Access Token
    echo  has the 'workflow' scope checked at:
    echo  https://github.com/settings/tokens
    echo ===================================================
)
echo.
pause
