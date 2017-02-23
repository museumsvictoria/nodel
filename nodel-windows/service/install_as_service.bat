@echo off
SET RELEASE=nodelhost-dev-2.1.1-rev234.jar
echo. Nodel Host Windows Service installer
echo.
echo. (Press Ctrl-C to stop at any time)
echo.

rem Make sure Java.exe can be run
where java > nul
if ERRORLEVEL 1 goto NoJava

echo Checking Java architechture...
pause

java -d64 -version 2> nul
if ERRORLEVEL 0 goto GotJava64

:GotJava86
echo (Java 32-bit)
echo Establishing 32-bit Java service wrapper...
pause
copy NodelHostsvc.exe.x86 ..\NodelHostsvc.exe

goto CopyJAR

:GotJava64
echo (Java 64-bit)
echo Establishing 64-bit Java service wrapper...
pause
copy NodelHostsvc.exe.x64 ..\NodelHostsvc.exe

goto CopyJAR


:CopyJAR
echo Copying versioned JAR to static JAR for static entry-point... (%RELEASE%)
copy ..\%RELEASE% ..\nodelhost.jar /Y
echo ...copy done!
echo.



echo Stopping any existing nodel services...
pause
..\NodelHostsvc.exe //SS
echo ...stop done!
echo.

echo Registering the (new) "Nodel Host" service...
pause
..\NodelHostsvc.exe //IS --DisplayName "Nodel Host" --Description "(see github.com/museumsvictoria/nodel)" --Startup auto --StartMode jvm --StartClass org.nodel.nodelhost.Service --StartMethod start --StopMode jvm --StopClass org.nodel.nodelhost.Service --StopMethod stop --LogPath .\logs --Classpath nodelhost.jar
echo ...service registration complete!
echo.

echo Starting the "Nodel Host" service...
pause
..\NodelHostsvc.exe //ES
echo ...service start done!
echo.

echo No more steps.
pause

GOTO END

:NoJava
echo Could not detect the Java platform. Please download from https://java.com/en/download/.
pause
GOTO END

:NoNodelHostJar
echo Please ensure latest version of Nodel Host release exists in "latestVersion" folder (e.g. "%RELEASE%")
echo (one can be downloaded from https://github.com/museumsvictoria/nodel)
pause
GOTO END

:End