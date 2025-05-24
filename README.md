# Como Testar

## Pré-requisitos

Certifique-se de que os seguintes softwares estejam instalados no seu sistema:

- **Java 11+** (para os servidores)  
- **Python 3.7+** (para os clientes Python)  
- **Node.js 14+** (para os clientes JavaScript)  
- **Maven** (para compilação dos servidores Java)  
- **ZeroMQ** (biblioteca de mensageria utilizada na comunicação)

## Execução

1. **Inicie os servidores**  
   Execute o arquivo `run-servers.bat` localizado na pasta `Server`. Aguarde até que todos os serviços sejam inicializados.

2. **Inicie os clientes**  
   Execute o arquivo `run-clients.bat` localizado na pasta `Client`.

## Resultado Esperado

Após a execução dos passos acima, serão abertas:

- **4 janelas de terminal** para:
  - 3 servidores
  - 1 balanceador de carga

- **5 janelas de terminal** para os clientes:
  - 3 clientes em Python
  - 2 clientes em JavaScript
