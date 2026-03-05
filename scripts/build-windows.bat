@echo off
chcp 1252 >nul
title Revisor Build

echo.
echo ==========================================
echo      REVISOR - Build para Windows
echo ==========================================
echo.

cd /d "%~dp0.."
echo [OK] Pasta do projeto: %CD%

if not exist "pom.xml" (
    echo [ERRO] pom.xml nao encontrado em %CD%
    pause & exit /b 1
)
echo [OK] pom.xml encontrado

java -version >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Java nao encontrado. Baixe em: https://adoptium.net/
    pause & exit /b 1
)
echo [OK] Java encontrado

jpackage --version >nul 2>&1
if errorlevel 1 (
    echo [ERRO] jpackage nao encontrado. Use JDK 17 ou superior.
    pause & exit /b 1
)
echo [OK] jpackage disponivel

:: ---- Verificar WiX ---------------------------------------------------------
set WIX_OK=0
candle --version >nul 2>&1
if not errorlevel 1 set WIX_OK=1
wix --version >nul 2>&1
if not errorlevel 1 set WIX_OK=1
if exist "C:\Program Files (x86)\WiX Toolset v3.11\bin\candle.exe" set WIX_OK=1
if exist "C:\Program Files\WiX Toolset v3.11\bin\candle.exe"       set WIX_OK=1

if "%WIX_OK%"=="1" (
    echo [OK] WiX Toolset encontrado - sera gerado instalador .exe
) else (
    echo [AVISO] WiX nao encontrado - apenas app-image sera gerado
)

:: ---- Localizar Maven -------------------------------------------------------
set MVN=

mvn -version >nul 2>&1
if not errorlevel 1 (
    set MVN=mvn
    echo [OK] Maven no PATH
    goto :maven_ok
)

for %%D in (
    "%USERPROFILE%\apache-maven-3.9.12"
    "%USERPROFILE%\apache-maven-3.9*"
    "%USERPROFILE%\apache-maven-3.8*"
    "%USERPROFILE%\apache-maven*"
    "C:\apache-maven*"
    "C:\maven*"
    "C:\Program Files\apache-maven*"
    "C:\tools\maven*"
    "C:\dev\apache-maven*"
) do (
    if exist "%%~D\bin\mvn.cmd" (
        set MVN="%%~D\bin\mvn.cmd"
        echo [OK] Maven: %%~D
        goto :maven_ok
    )
)

echo [ERRO] Maven nao encontrado.
pause & exit /b 1

:maven_ok
:: ---------------------------------------------------------------------------

:: ---- Limpar build anterior -------------------------------------------------
echo.
echo Limpando build anterior...
if exist "target" (
    robocopy "%TEMP%\vazio_nao_existe" "target" /MIR /NFL /NDL /NJH /NJS /NC /NS /NP >nul 2>&1
    rmdir /s /q "target" 2>nul
    if exist "target" (
        echo [AVISO] Nao foi possivel limpar target. Feche o IntelliJ e tente novamente.
    ) else (
        echo [OK] target limpo
    )
)

:: ---- [1/3] Compilar + copiar dependencias ----------------------------------
echo.
echo [1/3] Compilando e copiando dependencias...
echo       (primeira vez pode demorar 5-15 min)
echo.

:: Compila o JAR principal
call %MVN% package -q --no-transfer-progress -DskipTests
if errorlevel 1 (
    echo [ERRO] Falha na compilacao. Execute: %MVN% package
    pause & exit /b 1
)

:: Copia todas as dependencias para target\libs\
call %MVN% dependency:copy-dependencies -q --no-transfer-progress -DoutputDirectory=target\libs -DincludeScope=runtime
if errorlevel 1 (
    echo [ERRO] Falha ao copiar dependencias.
    pause & exit /b 1
)

:: Localizar o JAR principal gerado (Revisor-*.jar, excluindo os de libs)
set MAIN_JAR=
for %%F in (target\Revisor-*.jar target\Revisor.jar) do (
    echo %%F | findstr /i "original" >nul
    if errorlevel 1 (
        if not defined MAIN_JAR (
            set MAIN_JAR=%%~nxF
            echo [OK] JAR principal: %%~nxF
        )
    )
)

if not defined MAIN_JAR (
    echo [ERRO] JAR principal nao encontrado em target\
    echo Arquivos encontrados:
    dir target\*.jar /b 2>nul
    pause & exit /b 1
)

if not exist "target\libs" (
    echo [ERRO] target\libs\ nao encontrada.
    pause & exit /b 1
)
echo [OK] Dependencias copiadas para target\libs\

:: ---- Localizar icone -------------------------------------------------------
set ICON_ARG=
for %%P in (
    "src\main\resources\dev\revisor\revisor\revisor.ico"
    "src\main\resources\icons\revisor.ico"
) do (
    if exist %%P (
        set ICON_ARG=--icon %%P
        echo [OK] Icone: %%P
        goto :icon_ok
    )
)
echo [AVISO] Icone .ico nao encontrado
:icon_ok

:: ---- Preparar pasta de input para jpackage ---------------------------------
:: jpackage precisa do JAR principal + todas as deps na mesma pasta
if exist "jpackage-input" rmdir /s /q jpackage-input
mkdir jpackage-input
copy "target\%MAIN_JAR%" "jpackage-input\" >nul
xcopy "target\libs\*" "jpackage-input\" /Q /Y >nul
echo [OK] Pasta de input preparada

if exist "instalador" rmdir /s /q instalador
mkdir instalador

:: ---- [2/3] App-image -------------------------------------------------------
echo.
echo [2/3] Gerando executavel...

jpackage ^
    --type app-image ^
    --name "Revisor" ^
    --app-version "1.0.0" ^
    --vendor "Revisor App" ^
    --input jpackage-input ^
    --main-jar %MAIN_JAR% ^
    --main-class dev.revisor.revisor.programa.Launcher ^
    --dest instalador ^
    %ICON_ARG% ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Xmx512m"

if errorlevel 1 (
    echo [ERRO] jpackage falhou.
    pause & exit /b 1
)
echo [OK] Executavel: instalador\Revisor\Revisor.exe

:: ---- [3/3] Instalador .exe (so com WiX) ------------------------------------
if "%WIX_OK%"=="0" goto :resultado

echo.
echo [3/3] Gerando instalador .exe...

jpackage ^
    --type exe ^
    --name "Revisor" ^
    --app-version "1.0.0" ^
    --vendor "Revisor App" ^
    --input jpackage-input ^
    --main-jar %MAIN_JAR% ^
    --main-class dev.revisor.revisor.programa.Launcher ^
    --dest instalador ^
    %ICON_ARG% ^
    --win-dir-chooser ^
    --win-menu ^
    --win-menu-group "Revisor" ^
    --win-shortcut ^
    --win-per-user-install ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Xmx512m"

if errorlevel 1 (
    echo [AVISO] Instalador .exe falhou, mas o executavel foi gerado.
) else (
    echo [OK] Instalador: instalador\Revisor-1.0.0.exe
)

:: ---- Limpar pasta temporaria -----------------------------------------------
rmdir /s /q jpackage-input 2>nul

:resultado
echo.
echo ==========================================
echo  Resultado:
echo ==========================================
dir /b instalador\ 2>nul
echo.
echo  Executavel direto:
echo    instalador\Revisor\Revisor.exe
if "%WIX_OK%"=="1" (
    echo.
    echo  Instalador para distribuir:
    echo    instalador\Revisor-1.0.0.exe
)
echo ==========================================
echo.
pause
