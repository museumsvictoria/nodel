@echo off
echo.
echo. (Press Ctrl-C to stop at any time)
echo.
echo Dumping Java version...
java -d64 -version
echo.
echo Before continuing, ensure there is indication of the 64-bit Java platform in the dump above. If not, please use the 32-bit (x86) batch file installer.
pause
echo.

echo Listing versioned JAR files...
dir latestVersion\nodelhost-* /b
if ERRORLEVEL 1 GOTO NoNodelHostJar
echo.

echo Copying versioned JAR to static JAR for static entry-point...
pause
for %%A in (latestVersion\nodelhost-*.jar) do (
  echo %%A
  copy %%A nodelhost.jar
  goto NextStep
)

:NextStep

echo ...copy done!
echo.

echo Establishing 64-bit Java service wrapper...
pause
copy NodelHostsvc.exe.x64_rename NodelHostsvc.exe


echo Stopping any existing nodel services...
pause
NodelHostsvc.exe //SS
echo ...stop done!
echo.

echo Registering the (new) "Nodel Host" service...
pause
NodelHostsvc.exe //IS --DisplayName "Nodel Host" --Description "(see github.com/museumsvictoria/nodel)" --Startup auto --StartMode jvm --StartClass org.nodel.nodelhost.Service --StartMethod start --StopMode jvm --StopClass org.nodel.nodelhost.Service --StopMethod stop --LogPath .\logs --Classpath .\nodelhost.jar
echo ...service registration complete!
echo.

echo Starting the "Nodel Host" service...
pause
NodelHostsvc.exe //ES
echo ...service start done!
echo.

echo No more steps.
pause

GOTO END

:NoJava
echo Could not detect the Java platform. Please download from https://java.com/en/download/.

GOTO END

:NoNodelHostJar
echo Please ensure latest version of Nodel Host release exists in "latestVersion" folder (e.g. "nodelhost-release-2.1.1-rev234.jar")
echo (one can be downloaded from https://github.com/museumsvictoria/nodel)
GOTO END

:End