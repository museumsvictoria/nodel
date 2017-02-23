@echo off
echo Renaming versioned JAR to static JAR for static entry-point...
pause
copy ren nodelhost-*.jar nodelhost.jar
echo ...rename done!
echo.

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
