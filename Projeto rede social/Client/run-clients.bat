@echo off
echo ===============================================
echo    INICIANDO CLIENTES DA REDE SOCIAL
echo ===============================================
echo.
echo Iniciando 3 clientes Python e 2 clientes JavaScript...
echo.

REM Verifica se os arquivos dos clientes existem
if not exist "Python\social_network_client.py" (
    echo ERRO: Arquivo Python\social_network_client.py nao encontrado!
    echo Certifique-se de que o arquivo esta na pasta Python.
    pause
    exit /b 1
)

if not exist "Js\social_network_client.js" (
    echo ERRO: Arquivo Js\social_network_client.js nao encontrado!
    echo Certifique-se de que o arquivo esta na pasta Js.
    pause
    exit /b 1
)

REM Aguarda um pouco para garantir que o sistema esteja pronto
timeout /t 2 /nobreak >nul

echo Iniciando Cliente Python 1...
start "Cliente Python 1" cmd /k "cd /d Python && python social_network_client.py && pause"

REM Pequena pausa entre inicializações
timeout /t 1 /nobreak >nul

echo Iniciando Cliente Python 2...
start "Cliente Python 2" cmd /k "cd /d Python && python social_network_client.py && pause"

timeout /t 1 /nobreak >nul

echo Iniciando Cliente Python 3...
start "Cliente Python 3" cmd /k "cd /d Python && python social_network_client.py && pause"

timeout /t 1 /nobreak >nul

echo Iniciando Cliente JavaScript 1...
start "Cliente JavaScript 1" cmd /k "cd /d Js && node social_network_client.js && pause"

timeout /t 1 /nobreak >nul

echo Iniciando Cliente JavaScript 2...
start "Cliente JavaScript 2" cmd /k "cd /d Js && node social_network_client.js && pause"
