@echo off
chcp 1252 >nul
title Corrigir pasta target

echo.
echo ==========================================
echo   CORRIGIR pasta target com loop infinito
echo ==========================================
echo.

cd /d "%~dp0"
echo Pasta atual: %CD%

if not exist "target" (
    echo Pasta target nao encontrada aqui.
    echo Execute este script na raiz do projeto Revisor.
    pause & exit /b 1
)

echo Encontrada pasta target. Quebrando symlinks e removendo...
echo.

:: Usar robocopy para sincronizar uma pasta VAZIA sobre target
:: Isso apaga o conteudo sem seguir symlinks
echo [1/3] Criando pasta vazia temporaria...
if exist "_vazio_temp" rmdir "_vazio_temp"
mkdir "_vazio_temp"

echo [2/3] Sobrescrevendo target com pasta vazia (pode demorar)...
robocopy "_vazio_temp" "target" /MIR /NFL /NDL /NJH /NJS /NC /NS /NP >nul 2>&1

echo [3/3] Removendo pastas agora vazias...
rmdir /s /q "target" 2>nul
rmdir "_vazio_temp" 2>nul

if exist "target" (
    echo.
    echo [AVISO] target ainda existe. Tentando via PowerShell...
    powershell -Command "Remove-Item -Path 'target' -Recurse -Force -ErrorAction SilentlyContinue"
)

if exist "target" (
    echo [AVISO] Ainda nao removido. Tente reiniciar o PC e rodar novamente.
) else (
    echo.
    echo [OK] Pasta target removida com sucesso!
)

echo.
pause
