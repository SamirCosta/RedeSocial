package com.redesocial.repository;

import com.redesocial.model.Post;
import com.redesocial.util.EventLogger;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PostRepository {
    private final Map<String, Post> postsById = new ConcurrentHashMap<>();
    private final String dataFilePath;
    private final EventLogger logger;

    public PostRepository(String dataFilePath, EventLogger logger) {
        this.dataFilePath = dataFilePath;
        this.logger = logger;
        loadPosts();
    }

    @SuppressWarnings("unchecked")
    private void loadPosts() {
        File file = new File(dataFilePath);
        if (!file.exists()) {
            logger.log("Arquivo de publicações não encontrado, iniciando com repositório vazio");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, Post> loadedPosts = (Map<String, Post>) ois.readObject();

            postsById.clear();
            postsById.putAll(loadedPosts);

            logger.log("Carregadas " + postsById.size() + " publicações do arquivo");
        } catch (Exception e) {
            logger.logError("Erro ao carregar publicações do arquivo", e);
        }
    }

    private void savePosts() {
        try {
            File file = new File(dataFilePath);
            file.getParentFile().mkdirs();

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(postsById);
            }

            logger.log("Salvas " + postsById.size() + " publicações no arquivo");
        } catch (Exception e) {
            logger.logError("Erro ao salvar publicações no arquivo", e);
        }
    }

    public synchronized boolean addPost(Post post) {
        if (postsById.containsKey(post.getId())) {
            return false;
        }

        postsById.put(post.getId(), post);

        savePosts();
        logger.log("Publicação adicionada: " + post.getId() + " do usuário " + post.getUsername());

        return true;
    }

    public synchronized boolean updatePost(Post post) {
        if (!postsById.containsKey(post.getId())) {
            return false;
        }

        postsById.put(post.getId(), post);

        savePosts();
        logger.log("Publicação atualizada: " + post.getId());

        return true;
    }

    public synchronized boolean removePost(String postId) {
        if (!postsById.containsKey(postId)) {
            return false;
        }

        Post removedPost = postsById.remove(postId);

        savePosts();
        logger.log("Publicação removida: " + postId + " do usuário " + removedPost.getUsername());

        return true;
    }

    public Post getPostById(String postId) {
        return postsById.get(postId);
    }

    public List<Post> getPostsByUsername(String username) {
        return postsById.values().stream()
                .filter(post -> post.getUsername().equals(username))
                .collect(Collectors.toList());
    }

    public List<Post> getRecentPostsByUsers(List<String> usernames, int limit) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyList();
        }

        List<Post> result = postsById.values().stream()
                .filter(post -> usernames.contains(post.getUsername()))
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .limit(limit)
                .collect(Collectors.toList());

        return result;
    }

    public List<Post> getAllPosts() {
        return new ArrayList<>(postsById.values());
    }
}