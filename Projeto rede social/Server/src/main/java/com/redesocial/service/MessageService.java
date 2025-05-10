package com.redesocial.service;

import com.redesocial.model.Message;
import com.redesocial.model.User;
import com.redesocial.repository.MessageRepository;
import com.redesocial.repository.UserRepository;
import com.redesocial.util.EventLogger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serviço para gerenciar mensagens privadas entre usuários
 */
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EventLogger logger;
    private final String bindAddress;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    /**
     * Construtor do serviço de mensagens
     */
    public MessageService(MessageRepository messageRepository, UserRepository userRepository,
                          EventLogger logger, String address, int port) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.logger = logger;
        this.bindAddress = "tcp://" + address + ":" + (port + 100); // Porta diferente do PostService
        this.executor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Inicia o serviço de mensagens
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.submit(this::runService);
        }
    }

    /**
     * Para o serviço de mensagens
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
            logger.log("Serviço de mensagens parado");
        }
    }

    /**
     * Loop principal do serviço
     */
    private void runService() {
        try (ZContext context = new ZContext()) {
            // Socket REP para responder às requisições
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind(bindAddress);

            logger.log("Serviço de mensagens iniciado em " + bindAddress);

            while (running.get()) {
                // Aguarda uma requisição
                byte[] request = socket.recv();
                if (request == null) {
                    continue;
                }

                String requestStr = new String(request, StandardCharsets.UTF_8);
                logger.log("Requisição recebida no serviço de mensagens: " + requestStr);

                // Processa a requisição
                String response = processRequest(requestStr);

                // Envia a resposta
                socket.send(response.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de mensagens", e);
        }
    }

    /**
     * Processa uma requisição recebida
     */
    private String processRequest(String requestStr) {
        try {
            JSONObject request = new JSONObject(requestStr);
            String action = request.getString("action");

            switch (action) {
                case "SEND_MESSAGE":
                    return sendMessage(
                            request.getString("senderUsername"),
                            request.getString("receiverUsername"),
                            request.getString("content")
                    );
                case "MARK_AS_READ":
                    return markMessageAsRead(
                            request.getString("messageId"),
                            request.getString("username")
                    );
                case "GET_CONVERSATION":
                    return getConversation(
                            request.getString("username1"),
                            request.getString("username2")
                    );
                case "GET_UNREAD_MESSAGES":
                    return getUnreadMessages(
                            request.getString("username")
                    );
                default:
                    return createErrorResponse("Ação desconhecida: " + action);
            }
        } catch (Exception e) {
            logger.logError("Erro ao processar requisição", e);
            return createErrorResponse("Erro ao processar requisição: " + e.getMessage());
        }
    }

    /**
     * Envia uma mensagem para outro usuário
     */
    private String sendMessage(String senderUsername, String receiverUsername, String content) {
        // Verificar se os usuários existem
        User sender = userRepository.getUserByUsername(senderUsername);
        User receiver = userRepository.getUserByUsername(receiverUsername);

        if (sender == null) {
            return createErrorResponse("Remetente não encontrado");
        }

        if (receiver == null) {
            return createErrorResponse("Destinatário não encontrado");
        }

        try {
            // Gerar ID único para a mensagem
            String messageId = UUID.randomUUID().toString();

            // Criar e salvar a mensagem
            Message message = new Message(messageId, senderUsername, receiverUsername, content);
            boolean success = messageRepository.addMessage(message);

            if (success) {
                logger.log("Mensagem enviada com sucesso: " + messageId + " de " +
                        senderUsername + " para " + receiverUsername);

                // Registrar para replicação
                ReplicationManager.getInstance().registerMessageSent(message);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Mensagem enviada com sucesso");
                response.put("messageId", message.getId());
                response.put("sentAt", message.getSentAt().toString());
                return response.toString();
            } else {
                return createErrorResponse("Falha ao enviar mensagem");
            }
        } catch (Exception e) {
            logger.logError("Erro ao enviar mensagem", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    /**
     * Marca uma mensagem como lida
     */
    private String markMessageAsRead(String messageId, String username) {
        // Verificar se a mensagem existe
        Message message = messageRepository.getMessageById(messageId);
        if (message == null) {
            return createErrorResponse("Mensagem não encontrada");
        }

        // Verificar se o usuário é o destinatário da mensagem
        if (!message.getReceiverUsername().equals(username)) {
            return createErrorResponse("Apenas o destinatário pode marcar a mensagem como lida");
        }

        // Verificar se a mensagem já está marcada como lida
        if (message.isRead()) {
            return createErrorResponse("A mensagem já está marcada como lida");
        }

        try {
            // Marcar a mensagem como lida
            message.markAsRead();
            boolean success = messageRepository.updateMessage(message);

            if (success) {
                logger.log("Mensagem marcada como lida: " + messageId);

                // Registrar para replicação
                ReplicationManager.getInstance().registerMessageSent(message);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Mensagem marcada como lida com sucesso");
                response.put("messageId", message.getId());
                response.put("readAt", message.getReadAt().toString());
                return response.toString();
            } else {
                return createErrorResponse("Falha ao marcar mensagem como lida");
            }
        } catch (Exception e) {
            logger.logError("Erro ao marcar mensagem como lida", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    /**
     * Obtém o histórico de conversas entre dois usuários
     */
    private String getConversation(String username1, String username2) {
        // Verificar se os usuários existem
        User user1 = userRepository.getUserByUsername(username1);
        User user2 = userRepository.getUserByUsername(username2);

        if (user1 == null || user2 == null) {
            return createErrorResponse("Um ou ambos os usuários não encontrados");
        }

        try {
            // Buscar o histórico de conversas
            List<Message> conversation = messageRepository.getConversationHistory(username1, username2);

            JSONObject response = new JSONObject();
            response.put("success", true);

            JSONArray messagesArray = new JSONArray();
            for (Message message : conversation) {
                JSONObject messageJson = new JSONObject();
                messageJson.put("id", message.getId());
                messageJson.put("senderUsername", message.getSenderUsername());
                messageJson.put("receiverUsername", message.getReceiverUsername());
                messageJson.put("content", message.getContent());
                messageJson.put("sentAt", message.getSentAt().toString());
                messageJson.put("read", message.isRead());
                if (message.getReadAt() != null) {
                    messageJson.put("readAt", message.getReadAt().toString());
                }
                messagesArray.put(messageJson);
            }

            response.put("messages", messagesArray);
            response.put("count", conversation.size());

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao buscar histórico de conversas", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    /**
     * Obtém as mensagens não lidas para um usuário
     */
    private String getUnreadMessages(String username) {
        // Verificar se o usuário existe
        User user = userRepository.getUserByUsername(username);
        if (user == null) {
            return createErrorResponse("Usuário não encontrado");
        }

        try {
            // Buscar mensagens não lidas
            List<Message> unreadMessages = messageRepository.getUnreadMessagesByReceiver(username);

            JSONObject response = new JSONObject();
            response.put("success", true);

            JSONArray messagesArray = new JSONArray();
            for (Message message : unreadMessages) {
                JSONObject messageJson = new JSONObject();
                messageJson.put("id", message.getId());
                messageJson.put("senderUsername", message.getSenderUsername());
                messageJson.put("content", message.getContent());
                messageJson.put("sentAt", message.getSentAt().toString());
                messagesArray.put(messageJson);
            }

            response.put("messages", messagesArray);
            response.put("count", unreadMessages.size());

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao buscar mensagens não lidas", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    /**
     * Cria uma resposta de erro
     */
    private String createErrorResponse(String message) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("error", message);
        return response.toString();
    }
}