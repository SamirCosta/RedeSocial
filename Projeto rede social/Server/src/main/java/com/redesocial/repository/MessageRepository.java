package com.redesocial.repository;

import com.redesocial.model.Message;
import com.redesocial.util.EventLogger;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

            messagesById.clear();
            messagesById.putAll(loadedMessages);

            logger.log("Carregadas " + messagesById.size() + " mensagens do arquivo");
        } catch (Exception e) {
            logger.logError("Erro ao carregar mensagens do arquivo", e);
        }
    }

    private void saveMessages() {
        try {
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

    public synchronized boolean addMessage(Message message) {

        if (messagesById.containsKey(message.getId())) {
            return false;
        }

        messagesById.put(message.getId(), message);

        saveMessages();
        logger.log("Mensagem adicionada: " + message.getId() + " de " +
                message.getSenderUsername() + " para " + message.getReceiverUsername());

        return true;
    }

    public synchronized boolean updateMessage(Message message) {

        if (!messagesById.containsKey(message.getId())) {
            return false;
        }

        messagesById.put(message.getId(), message);

        saveMessages();
        logger.log("Mensagem atualizada: " + message.getId());

        return true;
    }

    public Message getMessageById(String messageId) {
        return messagesById.get(messageId);
    }

    public List<Message> getMessagesBySender(String username) {
        return messagesById.values().stream()
                .filter(message -> message.getSenderUsername().equals(username))
                .collect(Collectors.toList());
    }

    public List<Message> getMessagesByReceiver(String username) {
        return messagesById.values().stream()
                .filter(message -> message.getReceiverUsername().equals(username))
                .collect(Collectors.toList());
    }

    public List<Message> getUnreadMessagesByReceiver(String username) {
        return messagesById.values().stream()
                .filter(message -> message.getReceiverUsername().equals(username) && !message.isRead())
                .collect(Collectors.toList());
    }

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