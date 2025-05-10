package com.redesocial.repository;

import com.redesocial.model.Message;
import com.redesocial.util.EventLogger;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repositório para gerenciar mensagens privadas
 */
public class MessageRepository {
    private final Map<String, Message> messagesById = new ConcurrentHashMap<>();
    private final String dataFilePath;
    private final EventLogger logger;

    public MessageRepository(String dataFilePath, EventLogger logger) {
        this.dataFilePath = dataFilePath;
        this.logger = logger;
        loadMessages();
    }

    @SuppressWarnings("unchecked")
    private void loadMessages() {
        File file = new File(dataFilePath);
        if (!file.exists()) {
            logger.log("Arquivo de mensagens não encontrado, iniciando com repositório vazio");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, Message> loadedMessages = (Map<String, Message>) ois.readObject();

            // Limpa e adiciona as mensagens carregadas
            messagesById.clear();
            messagesById.putAll(loadedMessages);

            logger.log("Carregadas " + messagesById.size() + " mensagens do arquivo");
        } catch (Exception e) {
            logger.logError("Erro ao carregar mensagens do arquivo", e);
        }
    }

    private void saveMessages() {
        try {
            // Garante que o diretório existe
            File file = new File(dataFilePath);
            file.getParentFile().mkdirs();

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(messagesById);
            }

            logger.log("Salvas " + messagesById.size() + " mensagens no arquivo");
        } catch (Exception e) {
            logger.logError("Erro ao salvar mensagens no arquivo", e);
        }
    }

    /**
     * Adiciona uma nova mensagem
     *
     * @param message Mensagem a ser adicionada
     * @return true se a adição foi bem-sucedida, false caso contrário
     */
    public synchronized boolean addMessage(Message message) {
        // Verifica se a mensagem com o mesmo ID já existe
        if (messagesById.containsKey(message.getId())) {
            return false;
        }

        // Adiciona a mensagem
        messagesById.put(message.getId(), message);

        // Salva as alterações
        saveMessages();
        logger.log("Mensagem adicionada: " + message.getId() + " de " +
                message.getSenderUsername() + " para " + message.getReceiverUsername());

        return true;
    }

    /**
     * Atualiza uma mensagem existente (ex: marcar como lida)
     *
     * @param message Mensagem a ser atualizada
     * @return true se a atualização foi bem-sucedida, false caso contrário
     */
    public synchronized boolean updateMessage(Message message) {
        // Verifica se a mensagem existe
        if (!messagesById.containsKey(message.getId())) {
            return false;
        }

        // Atualiza a mensagem
        messagesById.put(message.getId(), message);

        // Salva as alterações
        saveMessages();
        logger.log("Mensagem atualizada: " + message.getId());

        return true;
    }

    /**
     * Busca uma mensagem pelo ID
     *
     * @param messageId ID da mensagem
     * @return A mensagem ou null se não encontrada
     */
    public Message getMessageById(String messageId) {
        return messagesById.get(messageId);
    }

    /**
     * Busca todas as mensagens enviadas por um usuário
     *
     * @param username Nome do usuário remetente
     * @return Lista de mensagens enviadas pelo usuário
     */
    public List<Message> getMessagesBySender(String username) {
        return messagesById.values().stream()
                .filter(message -> message.getSenderUsername().equals(username))
                .collect(Collectors.toList());
    }

    /**
     * Busca todas as mensagens recebidas por um usuário
     *
     * @param username Nome do usuário destinatário
     * @return Lista de mensagens recebidas pelo usuário
     */
    public List<Message> getMessagesByReceiver(String username) {
        return messagesById.values().stream()
                .filter(message -> message.getReceiverUsername().equals(username))
                .collect(Collectors.toList());
    }

    /**
     * Busca todas as mensagens não lidas recebidas por um usuário
     *
     * @param username Nome do usuário destinatário
     * @return Lista de mensagens não lidas recebidas pelo usuário
     */
    public List<Message> getUnreadMessagesByReceiver(String username) {
        return messagesById.values().stream()
                .filter(message -> message.getReceiverUsername().equals(username) && !message.isRead())
                .collect(Collectors.toList());
    }

    /**
     * Busca todo o histórico de conversas entre dois usuários
     *
     * @param username1 Nome do primeiro usuário
     * @param username2 Nome do segundo usuário
     * @return Lista de mensagens trocadas entre os usuários, ordenadas por data de envio
     */
    public List<Message> getConversationHistory(String username1, String username2) {
        return messagesById.values().stream()
                .filter(message ->
                        (message.getSenderUsername().equals(username1) &&
                                message.getReceiverUsername().equals(username2)) ||
                                (message.getSenderUsername().equals(username2) &&
                                        message.getReceiverUsername().equals(username1)))
                .sorted((m1, m2) -> m1.getSentAt().compareTo(m2.getSentAt()))
                .collect(Collectors.toList());
    }
}