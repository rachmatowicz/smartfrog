@echo off
setlocal

if defined SFHOME goto continue1
  if exist "%cd%\sfDetachAndTerminate.bat" cd ..
  set SFHOME=%cd%
  cd bin
:continue1
if (%1) == () goto usage
if (%1)==(-?) GOTO help
if (%1) == (-p) GOTO usage
if (%2) == (-p) GOTO usage
if (%2) == () goto usage

call "%SFHOME%\bin\setSFProperties"

if defined USERNAMEPREFIX_ON goto modify
set APPNAME=%2
goto run

:modify
set APPNAME="%USERNAME%_%2"
:run
if (%3)==(-p) GOTO execute
rem %SFJVM% %SFCMDPARAMETERS%  org.smartfrog.SFSystem -a \"%2\":DETaTERM:::%1: -e
%SFJVM% %SFCMDPARAMETERS%  org.smartfrog.SFSystem -a \"%APPNAME%\":DETaTERM:::%1: -e
GOTO end
:execute
if (%4)==() goto usage
%SFJVM% %SFCMDPARAMETERS%  org.smartfrog.SFSystem -a \"%APPNAME%\":DETaTERM:::%1: -p %4 -e


GOTO end
:usage
echo Insufficient arguments to use sfDetachAndTerminate
echo Usage: sfDetachAndTerminate HostName ComponentName [-p port]
exit /B 69
:help
echo Usage: sfDetachAndTerminate HostName ComponentName [-p port]
exit /B 0
:end
endlocal
