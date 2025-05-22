// social_network_client.js
const zmq = require('zeromq');
const readline = require('readline');

class SocialNetworkClient {
    constructor(balancerAddress = "localhost", balancerPort = 5000) {
        this.balancerAddress = balancerAddress;
        this.balancerPort = balancerPort;
        this.socket = null;
        this.currentUser = null;
        
        // Interface para entrada do usuário
        this.rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
    }

    async connect() {
        try {
            this.socket = new zmq.Request();
            const connectionString = `tcp://${this.balancerAddress}:${this.balancerPort}`;
            console.log(`Conectando ao balanceador: ${connectionString}`);
            await this.socket.connect(connectionString);
            console.log("Conectado com sucesso!");
        } catch (error) {
            console.error("Erro ao conectar:", error.message);
            throw error;
        }
    }

    async registerUser(username, password) {
        const request = {
            action: "USER_REGISTER",
            username: username,
            password: password
        };
        
        return await this._sendRequest(request);
    }

    async login(username, password) {
        const request = {
            action: "USER_LOGIN",
            username: username,
            password: password
        };
        
        const response = await this._sendRequest(request);
        if (response.success) {
            this.currentUser = username;
        }
        return response;
    }

    async createPost(content) {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "CREATE_POST",
            username: this.currentUser,
            content: content
        };
        
        return await this._sendRequest(request);
    }

    async getUserPosts(username = null) {
        if (!username && !this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const targetUser = username || this.currentUser;
        
        const request = {
            action: "GET_USER_POSTS",
            username: targetUser
        };
        
        return await this._sendRequest(request);
    }

    async getFeed(limit = 10) {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "GET_FEED",
            username: this.currentUser,
            limit: limit
        };
        
        return await this._sendRequest(request);
    }

    async followUser(usernameToFollow) {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "FOLLOW_USER",
            followerUsername: this.currentUser,
            followedUsername: usernameToFollow
        };
        
        return await this._sendRequest(request);
    }

    async unfollowUser(usernameToUnfollow) {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "UNFOLLOW_USER",
            followerUsername: this.currentUser,
            followedUsername: usernameToUnfollow
        };
        
        return await this._sendRequest(request);
    }

    async getFollowers(username = null) {
        if (!username && !this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const targetUser = username || this.currentUser;
        
        const request = {
            action: "GET_FOLLOWERS",
            username: targetUser
        };
        
        return await this._sendRequest(request);
    }

    async getFollowing(username = null) {
        if (!username && !this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const targetUser = username || this.currentUser;
        
        const request = {
            action: "GET_FOLLOWING",
            username: targetUser
        };
        
        return await this._sendRequest(request);
    }

    async sendMessage(receiverUsername, content) {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "SEND_MESSAGE",
            senderUsername: this.currentUser,
            receiverUsername: receiverUsername,
            content: content
        };
        
        return await this._sendRequest(request);
    }

    async getConversation(otherUsername) {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "GET_CONVERSATION",
            username1: this.currentUser,
            username2: otherUsername
        };
        
        return await this._sendRequest(request);
    }

    async getUnreadMessages() {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "GET_UNREAD_MESSAGES",
            username: this.currentUser
        };
        
        return await this._sendRequest(request);
    }

    async markMessageAsRead(messageId) {
        if (!this.currentUser) {
            return { success: false, error: "Você precisa fazer login primeiro" };
        }
        
        const request = {
            action: "MARK_AS_READ",
            messageId: messageId,
            username: this.currentUser
        };
        
        return await this._sendRequest(request);
    }

    async _sendRequest(request) {
        try {
            const requestJson = JSON.stringify(request);
            
            await this.socket.send(requestJson);
            const [responseBuffer] = await this.socket.receive();
            const responseJson = responseBuffer.toString();
            
            return JSON.parse(responseJson);
        } catch (error) {
            console.error(`Erro ao enviar/receber requisição: ${error.message}`);
            return { success: false, error: `Erro de comunicação: ${error.message}` };
        }
    }

    // Métodos auxiliares para input do usuário
    question(prompt) {
        return new Promise((resolve) => {
            this.rl.question(prompt, resolve);
        });
    }

    async waitForEnter() {
        await this.question("\nPressione Enter para continuar...");
    }

    close() {
        if (this.socket) {
            this.socket.close();
        }
        this.rl.close();
    }
}

// Funções auxiliares para exibição
function displayPosts(postsResponse) {
    if (!postsResponse.success) {
        console.log(`Erro: ${postsResponse.error}`);
        return;
    }
    
    const posts = postsResponse.posts || [];
    const count = postsResponse.count || 0;
    
    if (count === 0) {
        console.log("Nenhuma publicação encontrada.");
        return;
    }
    
    console.log(`\n=== ${count} Publicações ===`);
    posts.forEach(post => {
        const username = post.username;
        const content = post.content;
        const createdAt = post.createdAt;
        
        console.log(`\n@${username} - ${createdAt}`);
        console.log(`${content}`);
        console.log("-".repeat(40));
    });
}

function displayMessages(messagesResponse) {
    if (!messagesResponse.success) {
        console.log(`Erro: ${messagesResponse.error}`);
        return;
    }
    
    const messages = messagesResponse.messages || [];
    const count = messagesResponse.count || 0;
    
    if (count === 0) {
        console.log("Nenhuma mensagem encontrada.");
        return;
    }
    
    console.log(`\n=== ${count} Mensagens ===`);
    messages.forEach(msg => {
        const sender = msg.senderUsername;
        const content = msg.content;
        const sentAt = msg.sentAt;
        const read = msg.read || false;
        
        const status = read ? "[Lida]" : "[Não lida]";
        console.log(`\n${status} De: @${sender} - ${sentAt}`);
        console.log(`${content}`);
        console.log("-".repeat(40));
    });
}

function displayUsers(usersResponse, relationType = "Seguidores") {
    if (!usersResponse.success) {
        console.log(`Erro: ${usersResponse.error}`);
        return;
    }
    
    const users = usersResponse[relationType.toLowerCase()] || [];
    const count = usersResponse.count || 0;
    
    if (count === 0) {
        console.log(`Nenhum ${relationType.toLowerCase()} encontrado.`);
        return;
    }
    
    console.log(`\n=== ${count} ${relationType} ===`);
    users.forEach((username, index) => {
        console.log(`${index + 1}. @${username}`);
    });
}

// Função principal
async function main() {
    const balancerAddress = "localhost";
    const balancerPort = 5000;
    
    const client = new SocialNetworkClient(balancerAddress, balancerPort);
    
    try {
        await client.connect();
        
        while (true) {
            try {
                let choice;
                
                if (client.currentUser) {
                    console.log(`\n===== Rede Social - Logado como @${client.currentUser} =====`);
                    console.log("1. Criar publicação");
                    console.log("2. Ver minhas publicações");
                    console.log("3. Ver feed");
                    console.log("4. Seguir usuário");
                    console.log("5. Deixar de seguir usuário");
                    console.log("6. Ver seguidores");
                    console.log("7. Ver quem estou seguindo");
                    console.log("8. Enviar mensagem");
                    console.log("9. Ver mensagens não lidas");
                    console.log("10. Ver conversa com usuário");
                    console.log("11. Ver publicações de outro usuário");
                    console.log("12. Sair da conta");
                    console.log("0. Encerrar aplicação");
                } else {
                    console.log("\n===== Rede Social =====");
                    console.log("1. Cadastrar usuário");
                    console.log("2. Fazer login");
                    console.log("0. Encerrar aplicação");
                }
                
                choice = await client.question("\nEscolha uma opção: ");
                
                // Opções disponíveis sem login
                if (!client.currentUser) {
                    if (choice === "1") { // Cadastrar usuário
                        const username = await client.question("Digite o nome de usuário: ");
                        const password = await client.question("Digite a senha: ");
                        
                        if (!username || !password) {
                            console.log("Nome de usuário e senha são obrigatórios!");
                            continue;
                        }
                        
                        const response = await client.registerUser(username, password);
                        
                        if (response.success) {
                            console.log(`✓ Usuário ${response.username || username} cadastrado com sucesso!`);
                        } else {
                            console.log(`✗ Erro: ${response.error || 'Erro desconhecido'}`);
                        }
                    } else if (choice === "2") { // Fazer login
                        const username = await client.question("Digite o nome de usuário: ");
                        const password = await client.question("Digite a senha: ");
                        
                        if (!username || !password) {
                            console.log("Nome de usuário e senha são obrigatórios!");
                            continue;
                        }
                        
                        const response = await client.login(username, password);
                        
                        if (response.success) {
                            console.log(`✓ Login realizado com sucesso como @${username}!`);
                        } else {
                            console.log(`✗ Erro: ${response.error || 'Erro desconhecido'}`);
                        }
                    }
                } else {
                    // Opções disponíveis com login
                    switch (choice) {
                        case "1": { // Criar publicação
                            const content = await client.question("Digite o conteúdo da publicação: ");
                            
                            if (!content) {
                                console.log("O conteúdo da publicação é obrigatório!");
                                continue;
                            }
                            
                            const response = await client.createPost(content);
                            
                            if (response.success) {
                                console.log(`✓ Publicação criada com sucesso! ID: ${response.postId}`);
                            } else {
                                console.log(`✗ Erro: ${response.error || 'Erro desconhecido'}`);
                            }
                            break;
                        }
                        
                        case "2": { // Ver minhas publicações
                            const response = await client.getUserPosts();
                            displayPosts(response);
                            break;
                        }
                        
                        case "3": { // Ver feed
                            const limitInput = await client.question("Quantas publicações deseja ver? (Enter para padrão 10): ");
                            let limit = 10;
                            
                            if (limitInput) {
                                const parsed = parseInt(limitInput);
                                if (!isNaN(parsed)) {
                                    limit = parsed;
                                }
                            }
                            
                            const response = await client.getFeed(limit);
                            displayPosts(response);
                            break;
                        }
                        
                        case "4": { // Seguir usuário
                            const username = await client.question("Digite o nome do usuário que deseja seguir: ");
                            
                            if (!username) {
                                console.log("Nome de usuário é obrigatório!");
                                continue;
                            }
                            
                            const response = await client.followUser(username);
                            
                            if (response.success) {
                                console.log(`✓ Agora você está seguindo @${username}!`);
                            } else {
                                console.log(`✗ Erro: ${response.error || 'Erro desconhecido'}`);
                            }
                            break;
                        }
                        
                        case "5": { // Deixar de seguir usuário
                            const username = await client.question("Digite o nome do usuário que deseja deixar de seguir: ");
                            
                            if (!username) {
                                console.log("Nome de usuário é obrigatório!");
                                continue;
                            }
                            
                            const response = await client.unfollowUser(username);
                            
                            if (response.success) {
                                console.log(`✓ Você deixou de seguir @${username}!`);
                            } else {
                                console.log(`✗ Erro: ${response.error || 'Erro desconhecido'}`);
                            }
                            break;
                        }
                        
                        case "6": { // Ver seguidores
                            const response = await client.getFollowers();
                            displayUsers(response, "followers");
                            break;
                        }
                        
                        case "7": { // Ver quem estou seguindo
                            const response = await client.getFollowing();
                            displayUsers(response, "following");
                            break;
                        }
                        
                        case "8": { // Enviar mensagem
                            const receiver = await client.question("Digite o nome do usuário destinatário: ");
                            const content = await client.question("Digite o conteúdo da mensagem: ");
                            
                            if (!receiver || !content) {
                                console.log("Destinatário e conteúdo são obrigatórios!");
                                continue;
                            }
                            
                            const response = await client.sendMessage(receiver, content);
                            
                            if (response.success) {
                                console.log(`✓ Mensagem enviada para @${receiver}!`);
                            } else {
                                console.log(`✗ Erro: ${response.error || 'Erro desconhecido'}`);
                            }
                            break;
                        }
                        
                        case "9": { // Ver mensagens não lidas
                            const response = await client.getUnreadMessages();
                            displayMessages(response);
                            break;
                        }
                        
                        case "10": { // Ver conversa com usuário
                            const username = await client.question("Digite o nome do usuário da conversa: ");
                            
                            if (!username) {
                                console.log("Nome de usuário é obrigatório!");
                                continue;
                            }
                            
                            const response = await client.getConversation(username);
                            displayMessages(response);
                            break;
                        }
                        
                        case "11": { // Ver publicações de outro usuário
                            const username = await client.question("Digite o nome do usuário: ");
                            
                            if (!username) {
                                console.log("Nome de usuário é obrigatório!");
                                continue;
                            }
                            
                            const response = await client.getUserPosts(username);
                            displayPosts(response);
                            break;
                        }
                        
                        case "12": { // Sair da conta
                            console.log(`✓ Logout realizado. Até logo, @${client.currentUser}!`);
                            client.currentUser = null;
                            break;
                        }
                    }
                }
                
                // Opção disponível em qualquer estado
                if (choice === "0") {
                    console.log("Encerrando cliente...");
                    break;
                }
                
                // Pausa para leitura
                if (choice !== "0" && client.currentUser) {
                    await client.waitForEnter();
                }
            } catch (error) {
                console.error("Erro durante execução:", error.message);
                await client.waitForEnter();
            }
        }
    } catch (error) {
        console.error("Erro fatal:", error.message);
    } finally {
        client.close();
    }
}

// Configuração para tratar sinais de interrupção
process.on('SIGINT', () => {
    console.log('\nEncerrando aplicação...');
    process.exit(0);
});

process.on('SIGTERM', () => {
    console.log('\nEncerrando aplicação...');
    process.exit(0);
});

// Executa a aplicação
if (require.main === module) {
    main().catch(error => {
        console.error("Erro não tratado:", error);
        process.exit(1);
    });
}

module.exports = { SocialNetworkClient, displayPosts, displayMessages, displayUsers };