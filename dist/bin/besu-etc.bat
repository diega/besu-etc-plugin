@echo off
@rem Starts Besu on an ETC network from wherever this distribution was unpacked.
@rem
@rem The profile carries what the network is; this script carries the one thing a profile cannot
@rem name, which is where the installation lives. Besu resolves genesis-file against the working
@rem directory rather than against the profile, so the path is computed here from this script's own
@rem location and handed over as BESU_GENESIS_FILE. That sits below the command line in Besu's
@rem precedence, so --genesis-file still wins, as does a BESU_GENESIS_FILE already in the environment.

setlocal enabledelayedexpansion

for %%i in ("%~dp0..") do set "APP_HOME=%%~fi"

set "PROFILE=classic"
set "HAS_PROFILE="
set "GENESIS_ON_CLI="
set "PREV="
call :scan %*

if defined HAS_PROFILE (set "EXTRA=") else (set "EXTRA=--profile=classic")

if not defined GENESIS_ON_CLI (
  if not defined BESU_GENESIS_FILE set "BESU_GENESIS_FILE=%APP_HOME%\etc\%PROFILE%.json"
  if not exist "!BESU_GENESIS_FILE!" (
    echo besu-etc: no genesis file at !BESU_GENESIS_FILE! ^(profile '!PROFILE!'^) 1>&2
    exit /b 1
  )
)

call "%APP_HOME%\bin\besu.bat" %EXTRA% %*
exit /b %ERRORLEVEL%

@rem Both spellings of an option are accepted, so both are recognised here.
:scan
if "%~1"=="" exit /b
set "ARG=%~1"
if "!PREV!"=="--profile" set "PROFILE=!ARG!"
if "!ARG!"=="--profile" set "HAS_PROFILE=1"
if "!ARG:~0,10!"=="--profile=" (
  set "PROFILE=!ARG:~10!"
  set "HAS_PROFILE=1"
)
if "!ARG!"=="--genesis-file" set "GENESIS_ON_CLI=1"
if "!ARG:~0,15!"=="--genesis-file=" set "GENESIS_ON_CLI=1"
set "PREV=!ARG!"
shift
goto :scan
