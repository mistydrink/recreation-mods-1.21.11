@echo off
REM Builds every mod and collects the jars in .\jars
cd /d "%~dp0"
if not exist jars mkdir jars
for %%m in (deathkick nickskins smptools) do (
  echo == Building %%m
  pushd %%m
  call gradlew.bat build || (popd & exit /b 1)
  popd
  copy /Y %%m\build\libs\*.jar jars\ >nul
)
echo Done. Jars are in the jars folder.
