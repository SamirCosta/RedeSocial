# social_network_client_fixed.py
import zmq
import json
import sys
import time

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
        self.current_user = None
        
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
        # O balanceador deve encaminhar isso para o UserService
        request = {
            "action": "USER_REGISTER",  # A ação que o UserService espera
            "username": username,
            "password": password
        }
        
        response = self._send_request(request)
    
        if response.get("success") and response.get("replication_complete") is False:
            print("Aguardando replicação de dados...")
            time.sleep(2)
        
        return response
    
    def login(self, username, password):
        """
        Faz login no sistema.
        
        Args:
            username: Nome de usuário
            password: Senha do usuário
            
        Returns:
            dict: Resposta do servidor
        """
        # O balanceador deve encaminhar isso para o UserService
        request = {
            "action": "USER_LOGIN",
            "username": username,
            "password": password
        }
        
        response = self._send_request(request)
        if response.get("success", False):
            self.current_user = username
        return response
    
    def create_post(self, content):
        """
        Cria uma nova publicação.
        
        Args:
            content: Conteúdo da publicação
            
        Returns:
            dict: Resposta do servidor
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o PostService
        request = {
            "action": "CREATE_POST",
            "username": self.current_user,
            "content": content
        }
        
        return self._send_request(request)
    
    def get_user_posts(self, username=None):
        """
        Obtém as publicações de um usuário.
        
        Args:
            username: Nome do usuário (opcional, usa o usuário atual se não fornecido)
            
        Returns:
            dict: Resposta do servidor com as publicações
        """
        if not username and not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        target_user = username or self.current_user
        
        # O balanceador deve encaminhar isso para o PostService
        request = {
            "action": "GET_USER_POSTS",
            "username": target_user
        }
        
        return self._send_request(request)
    
    def get_feed(self, limit=10):
        """
        Obtém o feed de publicações para o usuário atual.
        
        Args:
            limit: Número máximo de publicações a serem retornadas
            
        Returns:
            dict: Resposta do servidor com as publicações do feed
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o PostService
        request = {
            "action": "GET_FEED",
            "username": self.current_user,
            "limit": limit
        }
        
        return self._send_request(request)
    
    def follow_user(self, username_to_follow):
        """
        Segue outro usuário.
        
        Args:
            username_to_follow: Nome do usuário a ser seguido
            
        Returns:
            dict: Resposta do servidor
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o FollowService
        request = {
            "action": "FOLLOW_USER",
            "followerUsername": self.current_user,
            "followedUsername": username_to_follow
        }
        
        return self._send_request(request)
    
    def unfollow_user(self, username_to_unfollow):
        """
        Deixa de seguir outro usuário.
        
        Args:
            username_to_unfollow: Nome do usuário a deixar de seguir
            
        Returns:
            dict: Resposta do servidor
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o FollowService
        request = {
            "action": "UNFOLLOW_USER",
            "followerUsername": self.current_user,
            "followedUsername": username_to_unfollow
        }
        
        return self._send_request(request)
    
    def get_followers(self, username=None):
        """
        Obtém os seguidores de um usuário.
        
        Args:
            username: Nome do usuário (opcional, usa o usuário atual se não fornecido)
            
        Returns:
            dict: Resposta do servidor com os seguidores
        """
        if not username and not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        target_user = username or self.current_user
        
        # O balanceador deve encaminhar isso para o FollowService
        request = {
            "action": "GET_FOLLOWERS",
            "username": target_user
        }
        
        return self._send_request(request)
    
    def get_following(self, username=None):
        """
        Obtém os usuários que um usuário está seguindo.
        
        Args:
            username: Nome do usuário (opcional, usa o usuário atual se não fornecido)
            
        Returns:
            dict: Resposta do servidor com os usuários seguidos
        """
        if not username and not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        target_user = username or self.current_user
        
        # O balanceador deve encaminhar isso para o FollowService
        request = {
            "action": "GET_FOLLOWING",
            "username": target_user
        }
        
        return self._send_request(request)
    
    def send_message(self, receiver_username, content):
        """
        Envia uma mensagem privada para outro usuário.
        
        Args:
            receiver_username: Nome do usuário destinatário
            content: Conteúdo da mensagem
            
        Returns:
            dict: Resposta do servidor
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o MessageService
        request = {
            "action": "SEND_MESSAGE",
            "senderUsername": self.current_user,
            "receiverUsername": receiver_username,
            "content": content
        }
        
        return self._send_request(request)
    
    def get_conversation(self, other_username):
        """
        Obtém o histórico de conversas com outro usuário.
        
        Args:
            other_username: Nome do outro usuário
            
        Returns:
            dict: Resposta do servidor com as mensagens
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o MessageService
        request = {
            "action": "GET_CONVERSATION",
            "username1": self.current_user,
            "username2": other_username
        }
        
        return self._send_request(request)
    
    def get_unread_messages(self):
        """
        Obtém as mensagens não lidas para o usuário atual.
        
        Returns:
            dict: Resposta do servidor com as mensagens não lidas
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o MessageService
        request = {
            "action": "GET_UNREAD_MESSAGES",
            "username": self.current_user
        }
        
        return self._send_request(request)
    
    def mark_message_as_read(self, message_id):
        """
        Marca uma mensagem como lida.
        
        Args:
            message_id: ID da mensagem
            
        Returns:
            dict: Resposta do servidor
        """
        if not self.current_user:
            return {"success": False, "error": "Você precisa fazer login primeiro"}
        
        # O balanceador deve encaminhar isso para o MessageService
        request = {
            "action": "MARK_AS_READ",
            "messageId": message_id,
            "username": self.current_user
        }
        
        return self._send_request(request)
    
    def _send_request(self, request):
        """
        Método privado para enviar requisições ao servidor.
        
        Args:
            request: Dicionário com a requisição
            
        Returns:
            dict: Resposta do servidor
        """
        # Converte para JSON e envia
        request_json = json.dumps(request)
        # print(f"Enviando requisição: {request_json}")
        self.socket.send_string(request_json)
        
        # Aguarda resposta
        response_json = self.socket.recv_string()
        # print(f"Resposta recebida: {response_json}")
        
        # Processa a resposta
        try:
            response = json.loads(response_json)
            return response
        except json.JSONDecodeError:
            print(f"Erro ao decodificar resposta: {response_json}")
            return {"success": False, "error": "Erro ao decodificar resposta do servidor"}
    
    def close(self):
        """Fecha a conexão com o servidor"""
        self.socket.close()
        self.context.term()


def display_posts(posts_response):
    """Exibe os posts de forma formatada"""
    if not posts_response.get("success"):
        print(f"Erro: {posts_response.get('error')}")
        return
    
    posts = posts_response.get("posts", [])
    count = posts_response.get("count", 0)
    
    if count == 0:
        print("Nenhuma publicação encontrada.")
        return
    
    print(f"\n=== {count} Publicações ===")
    for post in posts:
        username = post.get("username")
        content = post.get("content")
        created_at = post.get("createdAt")
        
        print(f"\n@{username} - {created_at}")
        print(f"{content}")
        print("-" * 40)


def display_messages(messages_response):
    """Exibe as mensagens de forma formatada"""
    if not messages_response.get("success"):
        print(f"Erro: {messages_response.get('error')}")
        return
    
    messages = messages_response.get("messages", [])
    count = messages_response.get("count", 0)
    
    if count == 0:
        print("Nenhuma mensagem encontrada.")
        return
    
    print(f"\n=== {count} Mensagens ===")
    for msg in messages:
        sender = msg.get("senderUsername")
        content = msg.get("content")
        sent_at = msg.get("sentAt")
        read = msg.get("read", False)
        
        status = "[Lida]" if read else "[Não lida]"
        print(f"\n{status} De: @{sender} - {sent_at}")
        print(f"{content}")
        print("-" * 40)


def display_users(users_response, relation_type="Seguidores"):
    """Exibe os usuários de forma formatada"""
    if not users_response.get("success"):
        print(f"Erro: {users_response.get('error')}")
        return
    
    users = users_response.get(relation_type.lower(), [])
    count = users_response.get("count", 0)
    
    if count == 0:
        print(f"Nenhum {relation_type.lower()} encontrado.")
        return
    
    print(f"\n=== {count} {relation_type} ===")
    for i, username in enumerate(users, 1):
        print(f"{i}. @{username}")


def main():
    # Configuração do balanceador
    balancer_address = "localhost"  # Endereço do balanceador
    balancer_port = 5000            # Porta do balanceador
    
    # Cria o cliente
    client = SocialNetworkClient(balancer_address, balancer_port)
    
    try:
        while True:
            if client.current_user:
                print(f"\n===== Rede Social - Logado como @{client.current_user} =====")
                print("1. Criar publicação")
                print("2. Ver minhas publicações")
                print("3. Ver feed")
                print("4. Seguir usuário")
                print("5. Deixar de seguir usuário")
                print("6. Ver seguidores")
                print("7. Ver quem estou seguindo")
                print("8. Enviar mensagem")
                print("9. Ver mensagens não lidas")
                print("10. Ver conversa com usuário")
                print("11. Ver publicações de outro usuário")
                print("12. Sair da conta")
                print("0. Encerrar aplicação")
            else:
                print("\n===== Rede Social =====")
                print("1. Cadastrar usuário")
                print("2. Fazer login")
                print("0. Encerrar aplicação")
            
            choice = input("\nEscolha uma opção: ")
            
            # Opções disponíveis sem login
            if not client.current_user:
                if choice == "1":  # Cadastrar usuário
                    username = input("Digite o nome de usuário: ")
                    password = input("Digite a senha: ")
                    
                    if not username or not password:
                        print("Nome de usuário e senha são obrigatórios!")
                        continue
                    
                    # Tenta registrar o usuário
                    response = client.register_user(username, password)
                    
                    if response.get("success"):
                        print(f"✓ Usuário {response.get('username', username)} cadastrado com sucesso!")
                    else:
                        print(f"✗ Erro: {response.get('error', 'Erro desconhecido')}")
                
                elif choice == "2":  # Fazer login
                    username = input("Digite o nome de usuário: ")
                    password = input("Digite a senha: ")
                    
                    if not username or not password:
                        print("Nome de usuário e senha são obrigatórios!")
                        continue
                    
                    # Tenta fazer login
                    response = client.login(username, password)
                    
                    if response.get("success"):
                        print(f"✓ Login realizado com sucesso como @{username}!")
                    else:
                        print(f"✗ Erro: {response.get('error', 'Erro desconhecido')}")
            
            # Opções disponíveis com login
            else:
                if choice == "1":  # Criar publicação
                    content = input("Digite o conteúdo da publicação: ")
                    
                    if not content:
                        print("O conteúdo da publicação é obrigatório!")
                        continue
                    
                    # Tenta criar a publicação
                    response = client.create_post(content)
                    
                    if response.get("success"):
                        print(f"✓ Publicação criada com sucesso! ID: {response.get('postId')}")
                    else:
                        print(f"✗ Erro: {response.get('error', 'Erro desconhecido')}")
                
                elif choice == "2":  # Ver minhas publicações
                    response = client.get_user_posts()
                    display_posts(response)
                
                elif choice == "3":  # Ver feed
                    limit = input("Quantas publicações deseja ver? (Enter para padrão 10): ")
                    try:
                        limit = int(limit) if limit else 10
                    except ValueError:
                        limit = 10
                    
                    response = client.get_feed(limit)
                    display_posts(response)
                
                elif choice == "4":  # Seguir usuário
                    username = input("Digite o nome do usuário que deseja seguir: ")
                    
                    if not username:
                        print("Nome de usuário é obrigatório!")
                        continue
                    
                    # Tenta seguir o usuário
                    response = client.follow_user(username)
                    
                    if response.get("success"):
                        print(f"✓ Agora você está seguindo @{username}!")
                    else:
                        print(f"✗ Erro: {response.get('error', 'Erro desconhecido')}")
                
                elif choice == "5":  # Deixar de seguir usuário
                    username = input("Digite o nome do usuário que deseja deixar de seguir: ")
                    
                    if not username:
                        print("Nome de usuário é obrigatório!")
                        continue
                    
                    # Tenta deixar de seguir o usuário
                    response = client.unfollow_user(username)
                    
                    if response.get("success"):
                        print(f"✓ Você deixou de seguir @{username}!")
                    else:
                        print(f"✗ Erro: {response.get('error', 'Erro desconhecido')}")
                
                elif choice == "6":  # Ver seguidores
                    response = client.get_followers()
                    display_users(response, "followers")
                
                elif choice == "7":  # Ver quem estou seguindo
                    response = client.get_following()
                    display_users(response, "following")
                
                elif choice == "8":  # Enviar mensagem
                    receiver = input("Digite o nome do usuário destinatário: ")
                    content = input("Digite o conteúdo da mensagem: ")
                    
                    if not receiver or not content:
                        print("Destinatário e conteúdo são obrigatórios!")
                        continue
                    
                    # Tenta enviar a mensagem
                    response = client.send_message(receiver, content)
                    
                    if response.get("success"):
                        print(f"✓ Mensagem enviada para @{receiver}!")
                    else:
                        print(f"✗ Erro: {response.get('error', 'Erro desconhecido')}")
                
                elif choice == "9":  # Ver mensagens não lidas
                    response = client.get_unread_messages()
                    display_messages(response)
                
                elif choice == "10":  # Ver conversa com usuário
                    username = input("Digite o nome do usuário da conversa: ")
                    
                    if not username:
                        print("Nome de usuário é obrigatório!")
                        continue
                    
                    response = client.get_conversation(username)
                    display_messages(response)
                
                elif choice == "11":  # Ver publicações de outro usuário
                    username = input("Digite o nome do usuário: ")
                    
                    if not username:
                        print("Nome de usuário é obrigatório!")
                        continue
                    
                    response = client.get_user_posts(username)
                    display_posts(response)
                
                elif choice == "12":  # Sair da conta
                    print(f"✓ Logout realizado. Até logo, @{client.current_user}!")
                    client.current_user = None
            
            # Opção disponível em qualquer estado
            if choice == "0":
                print("Encerrando cliente...")
                break
            
            # Pausa para leitura
            if choice != "0" and client.current_user:
                input("\nPressione Enter para continuar...")
    
    finally:
        client.close()


if __name__ == "__main__":
    main()