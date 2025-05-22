@echo off
setlocal enabledelayedexpansion
color 0B

echo ===============================================
echo    CONFIGURACAO DE DEPENDENCIAS
echo       SISTEMA DE REDE SOCIAL
echo ===============================================
echo.

echo Este script verificara e instalara as dependencias necessarias.
echo.
pause

echo Verificando Python...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Python nao foi encontrado!
    echo.
    echo Por favor, instale o Python 3.7+ do site oficial:
    echo https://www.python.org/downloads/
    echo.
    echo Certifique-se de marcar "Add Python to PATH" durante a instalacao.
    echo.
    pause
    exit /b 1
) else (
    echo [OK] Python encontrado:
    python --version
)

echo.
echo Verificando Node.js...
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Node.js nao foi encontrado!
    echo.
    echo Por favor, instale o Node.js do site oficial:
    echo https://nodejs.org/
    echo.
    echo Recomendamos a versao LTS (Long Term Support).
    echo.
    pause
    exit /b 1
) else (
    echo [OK] Node.js encontrado:
    node --version
)

echo.
echo ===============================================
echo      INSTALANDO DEPENDENCIAS PYTHON
echo ===============================================
echo.

echo Verificando pip...
pip --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [AVISO] pip nao encontrado. Tentando com python -m pip...
    python -m pip --version >nul 2>&1
    if !errorlevel! neq 0 (
        echo [ERRO] pip nao esta disponivel!
        pause
        exit /b 1
    ) else (
        set PIP_CMD=python -m pip
    )
) else (
    set PIP_CMD=pip
)

echo [OK] pip encontrado.
echo.

echo Instalando PyZMQ...
echo Comando: %PIP_CMD% install pyzmq
%PIP_CMD% install pyzmq
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Falha ao instalar PyZMQ com pip normal!
    echo Tentando com --user flag...
    %PIP_CMD% install --user pyzmq
    if !errorlevel! neq 0 (
        echo.
        echo [ERRO] Falha ao instalar PyZMQ!
        echo.
        echo Solucoes possiveis:
        echo 1. Execute este script como administrador
        echo 2. Verifique sua conexao com a internet
        echo 3. Tente instalar manualmente: pip install pyzmq
        echo 4. Use: python -m pip install pyzmq
        pause
        exit /b 1
    ) else (
        echo [OK] PyZMQ instalado com --user flag
    )
) else (
    echo [OK] PyZMQ instalado com sucesso
)

echo.
echo Verificando instalacao do PyZMQ...
python -c "import zmq; print('PyZMQ versao:', zmq.zmq_version())" 2>nul
if %errorlevel% neq 0 (
    echo [ERRO] PyZMQ nao foi instalado corretamente!
    pause
    exit /b 1
) else (
    echo [OK] PyZMQ instalado com sucesso!
    python -c "import zmq; print('PyZMQ versao:', zmq.zmq_version())"
)

echo.
echo ===============================================
echo     INSTALANDO DEPENDENCIAS NODE.JS
echo ===============================================
echo.

echo Verificando npm...
npm --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERRO] npm nao encontrado!
    echo npm geralmente vem com o Node.js. Reinstale o Node.js.
    pause
    exit /b 1
) else (
    echo [OK] npm encontrado:
    npm --version
)

echo.
echo Inicializando package.json se necessario...
if not exist "Js\package.json" (
    echo Criando package.json na pasta Js...
    if not exist "Js" mkdir Js
    echo {> Js\package.json
    echo   "name": "social-network-client",>> Js\package.json
    echo   "version": "1.0.0",>> Js\package.json
    echo   "description": "Cliente para sistema de rede social distribuida",>> Js\package.json
    echo   "main": "social_network_client.js",>> Js\package.json
    echo   "dependencies": {},>> Js\package.json
    echo   "author": "",>> Js\package.json
    echo   "license": "MIT">> Js\package.json
    echo }>> Js\package.json
)

echo.
echo Instalando ZeroMQ para Node.js...
cd Js
npm install zeromq
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Falha ao instalar zeromq para Node.js!
    echo.
    echo Isso pode acontecer devido a:
    echo 1. Problemas de compilacao (necessita Visual Studio Build Tools)
    echo 2. Conexao com internet
    echo 3. Permissoes de pasta
    echo.
    echo Tente instalar Visual Studio Build Tools ou use uma versao pre-compilada.
    cd ..
    pause
    exit /b 1
)
cd ..

echo.
echo Verificando instalacao do ZeroMQ para Node.js...
cd Js
node -e "const zmq = require('zeromq'); console.log('ZeroMQ para Node.js instalado com sucesso!');" 2>nul
if %errorlevel% neq 0 (
    echo [ERRO] ZeroMQ para Node.js nao foi instalado corretamente!
    cd ..
    pause
    exit /b 1
) else (
    echo [OK] ZeroMQ para Node.js instalado com sucesso!
)
cd ..

echo.
echo ===============================================
echo       VERIFICACAO FINAL
echo ===============================================
echo.

echo Verificando arquivos de cliente...
if exist "Python\social_network_client.py" (
    echo [OK] Cliente Python encontrado em Python\
) else (
    echo [AVISO] Python\social_network_client.py nao encontrado
    echo Certifique-se de que o arquivo esta presente na pasta Python antes de executar os clientes.
)

if exist "Js\social_network_client.js" (
    echo [OK] Cliente JavaScript encontrado em Js\
) else (
    echo [AVISO] Js\social_network_client.js nao encontrado
    echo Certifique-se de que o arquivo esta presente na pasta Js antes de executar os clientes.
)

echo.
echo ===============================================
echo      CONFIGURACAO CONCLUIDA!
echo ===============================================
echo.
echo Todas as dependencias foram instaladas com sucesso!
echo.
echo Voce agora pode executar:
echo - start_clients.bat (para iniciar clientes automaticamente)
echo - start_clients_advanced.bat (para opcoes avancadas)
echo.
echo OU executar manualmente:
echo - cd Python && python social_network_client.py
echo - cd Js && node social_network_client.js
echo.
pause