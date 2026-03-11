@echo off
SETLOCAL enableextensions enabledelayedexpansion
set ABASE=%~dp0
set LIBDIR=lib
if not exist "!ABASE!\!LIBDIR!" (
  set "TABASE=!ABASE!\..\..\..\target\libs"
  if exist "!TABASE!" (
    set "JBASE=!TABASE!"
    for %%D in ("!TABASE!") do set "LIBDIR=%%~nxD"
    for %%D in ("!JBASE!") do set "ABASE=%%~dpD"
    if "!ABASE:~-1!"=="\" set "ABASE=!ABASE:~0,-1!"
    del "!JBASE!\*-plain.jar" 2>nul
    for %%F in ("!JBASE!\*.jar") do set "JAR=%%F"
  ) else (
    for %%F in ("!ABASE!\*.jar") do set "JAR=%%F"
  )
)
if "x"=="x%TARGET%" set TARGET=%ABASE%\target
set USERINIT=%TARGET%\init.bat
if exist "!USERINIT!" call "!USERINIT!"
if "x"=="x%DEBUG%" set DEBUG=false
if "x"=="x%DTX_EXECUTABLE%" set DTX_EXECUTABLE=%~n0
if "%DTX_EXECUTABLE%"=="startup" set DTX_EXECUTABLE=init
set DTX_EXECUTABLE=%DTX_EXECUTABLE:startup-=%
if "x"=="x%DTX_JAVA_EXECUTABLE%" (
    if "x%JAVA_HOME%"=="x" (
      set "DTX_JAVA_EXECUTABLE=java"
    ) else (
      set "DTX_JAVA_EXECUTABLE=%JAVA_HOME%\bin\java"
    )
)
set DTX_DIR_NAME=.detox-utils
if "x"=="x%JBASE%" set JBASE=%ABASE%\%LIBDIR%
set ASPECTJ=aspectjweaver*.jar
set BASE=%ABASE%
set JARCH=32
set DTX_ENV=true
if x%DTX_ENV_HOME%==x set DTX_ENV=false
if %DTX_ENV%==true (
	set USERINIT=%DTX_ENV_HOME%\init.bat
	if exist "!USERINIT!" call "!USERINIT!"
)
if "x"=="x%DTX_USER_HOME%" set DTX_PROFILE_DIR=%DTX_USER_HOME%
if "x"=="x%DTX_PROFILE_DIR%" set DTX_PROFILE_DIR=%USERPROFILE%\%DTX_DIR_NAME%
set USERINIT=%DTX_PROFILE_DIR%\init.bat
if exist "%USERINIT%" call "%USERINIT%"
if %DTX_ENV%==true (
	set USERINIT=%DTX_ENV_HOME%\%DTX_EXECUTABLE%.bat
	if exist "!USERINIT!" call "!USERINIT!"
)
set USERINIT=%DTX_PROFILE_DIR%\%DTX_EXECUTABLE%.bat
if exist "!USERINIT!" call "!USERINIT!"
if x==x%STDIN% set STDIN=%~n0
if x==x%DTX_SHELL% if not "x%DTX_JAVA_EXECUTABLE:javaw=%"=="x%DTX_JAVA_EXECUTABLE%" set STDIN=&set DTX_SHELL=start "Shell-less java for %DTX_MAIN_CLASS%" /B
SET DTX_JAVA_EXECUTABLE_CLI=%DTX_JAVA_EXECUTABLE:javaw=java%
SET /A XCOUNT=0
set JARCH=32
for /f "usebackq tokens=3" %%g in (`cmd /c "%DTX_JAVA_EXECUTABLE_CLI%" -version 2^>^&1 ^| findstr /i "version Bit"`) do (
	SET /A XCOUNT += 1
	IF x!XCOUNT!==x1 set JAVAVER=%%g
	IF x!XCOUNT!==x2 set JARCH=%%g
)
set JAVAVER=%JAVAVER:"=%
set JARCH=%JARCH:-Bit=%
for /f "delims=. tokens=1-3" %%v in ("%JAVAVER%") do (
    set JAVAVER=%%v%%w
)
for %%F in (%JBASE%\%ASPECTJ%) do (
	set ASPECTJ=%%F
)

set "DTX_JARGS="
if exist "%ASPECTJ%" (
    set DTX_JARGS=!DTX_JARGS! "-javaagent:%ASPECTJ%"
)
set "PATH=%JBASE%\native;%PATH%"

if exist "%ABASE%\java-args.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%ABASE%\java-args.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if exist "%TARGET%\%~n0-args.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%TARGET%\%~n0-args.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if exist "%TARGET%\java-args-%JARCH%.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%TARGET%\java-args-%JARCH%.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if exist "%TARGET%\java-args.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%TARGET%\java-args.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if %DTX_ENV%==true if exist "%DTX_ENV_HOME%\%~n0-args.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%DTX_ENV_HOME%\%~n0-args.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if %DTX_ENV%==true if exist "%DTX_ENV_HOME%\java-args-%JARCH%.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%DTX_ENV_HOME%\java-args-%JARCH%.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if %DTX_ENV%==true if exist "%DTX_ENV_HOME%\java-args.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%DTX_ENV_HOME%\java-args.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if exist "%DTX_PROFILE_DIR%\%~n0-args.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%DTX_PROFILE_DIR%\%~n0-args.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if exist "%DTX_PROFILE_DIR%\java-args-%JARCH%.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%DTX_PROFILE_DIR%\java-args-%JARCH%.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"
if exist "%DTX_PROFILE_DIR%\java-args.txt" for /f "usebackq Tokens=* Delims=" %%x in ("%DTX_PROFILE_DIR%\java-args.txt") do set DTX_JARGS=!DTX_JARGS! "%%x"

if not "x%DEBUG:mgmtr=%"=="x%DEBUG%" set "DTX_DJARGS=%DTX_DJARGS% -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=5006 -Dcom.sun.management.jmxremote.ssl=false"
if not "x%DEBUG:mgmtl=%"=="x%DEBUG%" set "DTX_DJARGS=%DTX_DJARGS% -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=true"
if not "x%DEBUG:local=%"=="x%DEBUG%" set "DTX_DJARGS=%DTX_DJARGS% -Ddebug=true -Daj.weaving.verbose=true"
if not "x%DEBUG:remote=%"=="x%DEBUG%" set "DTX_DJARGS=%DTX_DJARGS% -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
if not "x%DEBUG:covc=%"=="x%DEBUG%" set "DTX_DJARGS=%DTX_DJARGS% -javaagent:%LIBDIR%/jacocoagent.jar=destfile=%TARGET%\%DTX_EXECUTABLE%.exec,append=true"
if not "x%DEBUG:covf=%"=="x%DEBUG%" set "DTX_DJARGS=%DTX_DJARGS% -javaagent:%LIBDIR%/jacocoagent.jar=destfile=%TARGET%\%DTX_EXECUTABLE%.exec,append=false,jmx=true"
for /F "usebackq tokens=2* delims=: " %%W in (`mode con ^| findstr Columns`) do set COLUMNS=%%W
set "DTX_JARGS=!DTX_JARGS! %DTX_DJARGS% -Dconsole_width=%COLUMNS% -splash:res/splash.jpg"
set CP_FILE=%TEMP%\jargs_%RANDOM%.txt
<nul set /p ="%DTX_PROFILE_DIR%/%LIBDIR%/cp;%JBASE%/cp;%DTX_CLASSPATH%" >>"%CP_FILE%"
if "x"=="x%JAR%" for %%J in ("%JBASE%\*.jar") do <nul set /p =";%%J" >>"%CP_FILE%"
if x%DTX_MAIN_CLASS%==x (
	set DTX_MAIN_CLASS=%~n0
	if a!DTX_MAIN_CLASS!==astartup set DTX_MAIN_CLASS=Main
	set DTX_MAIN_CLASS=hu.detox.!DTX_MAIN_CLASS!
)
if x%UPDATE%==x set UPDATE=%TARGET%/update
set BATCHUPDATE=0
for /F %%i in ("%UPDATE%") do if %%~ni==update set BATCHUPDATE=1
set UPF=%BASE%\delete.txt
if exist "%UPDATE%" (
	if %BATCHUPDATE%==1 (
		xcopy /q/s/y "%UPDATE%" "%BASE%"
		for /f "tokens=*" %%a in (%UPF%) do IF EXIST %BASE%\%%a\NUL ( rmdir /s/q %BASE%\%%a ) ELSE ( del /q %BASE%\%%a )
		del "%UPF%"
		rmdir /s/q "%UPDATE%"
	) else (
		set NDTX_SHELL=%DTX_SHELL%
		if NOT %NDTX_SHELL%x==x set NDTX_SHELL=%NDTX_SHELL% /WAIT
		%NDTX_SHELL% "%DTX_JAVA_EXECUTABLE%" !DTX_JARGS! -cp @"%CP_FILE%" -Dtarget=%TARGET% -Dbase=%BASE% hu.detox.launcher.Main
	)
)
if "x"=="x%JAR%" (
  set "DTX_JARGS=!DTX_JARGS! %DTX_MAIN_CLASS%"
) else (
  set "DTX_JARGS=!DTX_JARGS! -Dloader.main=%DTX_MAIN_CLASS% -jar %JAR%"
)
rem "%DTX_JAVA_EXECUTABLE%" -cp @"%CP_FILE%" -XshowSettings:property -version 2>&1
%DTX_SHELL% "%DTX_JAVA_EXECUTABLE%" -cp @"%CP_FILE%" -DstdIn=%STDIN% "-Dlogging.file.path=%TARGET%" -Dtarget=%TARGET% -Dbase=%BASE% !DTX_JARGS! %*

ENDLOCAL
