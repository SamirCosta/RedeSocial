@echo off
setlocal

echo Compilando o servidor...
call mvn package

echo Iniciando os servidores em janelas separadas...

:: Iniciar balancer
start cmd /k "java -jar target\Server-1.0-SNAPSHOT.jar balancer.properties"

:: Aguardar 5 segundos antes de iniciar o próximo servidor
timeout /t 5

:: Iniciar Servidor 1
start cmd /k "java -jar target\Server-1.0-SNAPSHOT.jar server1.properties"

:: Aguardar 5 segundos antes de iniciar o próximo servidor
timeout /t 5

:: Iniciar Servidor 2
start cmd /k "java -jar target\Server-1.0-SNAPSHOT.jar server2.properties"

:: Aguardar 5 segundos antes de iniciar o próximo servidor
timeout /t 5

:: Iniciar Servidor 3
start cmd /k "java -jar target\Server-1.0-SNAPSHOT.jar server3.properties"

echo Todos os servidores foram iniciados.
echo Para fechar os servidores, feche as janelas de comando correspondentes.
endlocal