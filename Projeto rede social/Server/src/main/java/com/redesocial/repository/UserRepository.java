package com.redesocial.repository;

import com.redesocial.model.User;
import com.redesocial.util.EventLogger;
import com.redesocial.util.Logger;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepository {
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final String dataFilePath;
    private final EventLogger logger;

    public UserRepository(String dataFilePath, EventLogger logger) {
        this.dataFilePath = dataFilePath;
        this.logger = logger;
        loadUsers();
    }

    @SuppressWarnings("unchecked")
    private void loadUsers() {
        File file = new File(dataFilePath);
        if (!file.exists()) {
            logger.log("Arquivo de usuários não encontrado, iniciando com repositório vazio");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, User> loadedUsers = (Map<String, User>) ois.readObject();

            // Limpa e adiciona os usuários carregados
            usersByUsername.clear();
            usersByUsername.putAll(loadedUsers);

            logger.log("Carregados " + usersByUsername.size() + " usuários do arquivo");
        } catch (Exception e) {
            logger.logError("Erro ao carregar usuários do arquivo", e);
        }
    }

    private void saveUsers() {
        try {
            logger.log("******************FILE PATH " + dataFilePath);
            // Garante que o diretório existe
            File file = new File(dataFilePath);
            file.getParentFile().mkdirs();

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            logger.log("****************** ESCREVEU");
                oos.writeObject(usersByUsername);
            }

            logger.log("Salvos " + usersByUsername.size() + " usuários no arquivo");
        } catch (Exception e) {
            logger.logError("Erro ao salvar usuários no arquivo", e);
        }
    }

    public synchronized boolean addUser(User user) {
        // Verifica se o username já existe (case insensitive)
        if (usersByUsername.containsKey(user.getUsername().toLowerCase())) {
            logger.log("************* JA EXISTE");
            return false;
        }

        // Adiciona o usuário (usando username em lowercase como chave para busca case-insensitive)
        usersByUsername.put(user.getUsername().toLowerCase(), user);

        // Salva as alterações
        saveUsers();
        logger.log("Usuário adicionado: " + user.getUsername());

        return true;
    }

    public User getUserByUsername(String username) {
        return usersByUsername.get(username.toLowerCase());
    }

    /**
     * Atualiza um usuário existente
     *
     * @param user Usuário a ser atualizado
     * @return true se a atualização foi bem-sucedida, false caso contrário
     */
    public synchronized boolean updateUser(User user) {
        if (user == null || !usersByUsername.containsKey(user.getUsername().toLowerCase())) {
            return false;
        }

        // Atualiza o usuário
        usersByUsername.put(user.getUsername().toLowerCase(), user);

        // Salva as alterações
        saveUsers();
        logger.log("Usuário atualizado: " + user.getUsername());

        return true;
    }
}