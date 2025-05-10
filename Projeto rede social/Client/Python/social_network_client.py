# social_network_client.py (modificado)
import zmq
import json
import sys

class SocialNetworkClient:
    def __init__(self, balancer_address="localhost", balancer_port=5000):
        """
        Inicializa o cliente da rede social.
        
        Args:
            balancer_address: Endereço do balanceador de carga
            balancer_port: Porta do balanceador
        """
        self.balancer_address = balancer_address
        self.balancer_port = balancer_port
        self.context = zmq.Context()
        self.socket = self.context.socket(zmq.REQ)
        self.connect()
        
    def connect(self):
        """Conecta ao balanceador de carga"""
        connection_string = f"tcp://{self.balancer_address}:{self.balancer_port}"
        print(f"Conectando ao balanceador: {connection_string}")
        self.socket.connect(connection_string)
        
    def register_user(self, username, password):
        """
        Registra um novo usuário no sistema.
        
        Args:
            username: Nome de usuário desejado
            password: Senha do usuário
            
        Returns:
            dict: Resposta do servidor
        """
        request = {
            "action": "register",
            "username": username,
            "password": password
        }
        
        # Converte para JSON e envia
        request_json = json.dumps(request)
        print(f"Enviando requisição: {request_json}")
        self.socket.send_string(request_json)
        
        # Aguarda resposta
        response_json = self.socket.recv_string()
        print(f"Resposta recebida: {response_json}")
        
        # Processa a resposta
        response = json.loads(response_json)
        return response
    
    def close(self):
        """Fecha a conexão com o servidor"""
        self.socket.close()
        self.context.term()


def main():
    # Configuração do balanceador
    balancer_address = "localhost"  # Endereço do balanceador
    balancer_port = 5000            # Porta do balanceador
    
    # Cria o cliente
    client = SocialNetworkClient(balancer_address, balancer_port)
    
    try:
        while True:
            print("\n===== Cliente de Rede Social =====")
            print("1. Cadastrar usuário")
            print("0. Sair")
            
            choice = input("Escolha uma opção: ")
            
            if choice == "1":
                username = input("Digite o nome de usuário: ")
                password = input("Digite a senha: ")
                
                if not username or not password:
                    print("Nome de usuário e senha são obrigatórios!")
                    continue
                
                # Tenta registrar o usuário
                response = client.register_user(username, password)
                
                if response.get("success"):
                    print(f"✓ Usuário {response.get('username')} cadastrado com sucesso!")
                else:
                    print(f"✗ Erro: {response.get('error')}")
            
            elif choice == "0":
                print("Encerrando cliente...")
                break
            
            else:
                print("Opção inválida!")
    
    finally:
        client.close()


if __name__ == "__main__":
    main()