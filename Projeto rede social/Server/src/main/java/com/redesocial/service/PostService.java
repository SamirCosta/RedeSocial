package com.redesocial.service;

import com.redesocial.model.Post;
import com.redesocial.model.User;
import com.redesocial.repository.PostRepository;
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

public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final EventLogger logger;
    private final String bindAddress;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    public PostService(PostRepository postRepository, UserRepository userRepository,
                       EventLogger logger, String address, int port) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.logger = logger;
        this.bindAddress = "tcp://" + address + ":" + port;
        this.executor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.submit(this::runService);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
            logger.log("Serviço de posts parado");
        }
    }

    private void runService() {
        try (ZContext context = new ZContext()) {

            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind(bindAddress);

            logger.log("Serviço de posts iniciado em " + bindAddress);

            while (running.get()) {

                byte[] request = socket.recv();
                if (request == null) {
                    continue;
                }

                String requestStr = new String(request, StandardCharsets.UTF_8);
                logger.log("Requisição recebida no serviço de posts: " + requestStr);

                String response = processRequest(requestStr);

                socket.send(response.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de posts", e);
        }
    }

    private String processRequest(String requestStr) {
        try {
            JSONObject request = new JSONObject(requestStr);
            String action = request.getString("action");

            switch (action) {
                case "CREATE_POST":
                    return createPost(
                            request.getString("username"),
                            request.getString("content")
                    );
                case "UPDATE_POST":
                    return updatePost(
                            request.getString("postId"),
                            request.getString("username"),
                            request.getString("content")
                    );
                case "DELETE_POST":
                    return deletePost(
                            request.getString("postId"),
                            request.getString("username")
                    );
                case "GET_USER_POSTS":
                    return getUserPosts(
                            request.getString("username")
                    );
                case "GET_FEED":
                    return getFeed(
                            request.getString("username"),
                            request.getInt("limit")
                    );
                default:
                    return createErrorResponse("Ação desconhecida: " + action);
            }
        } catch (Exception e) {
            logger.logError("Erro ao processar requisição", e);
            return createErrorResponse("Erro ao processar requisição: " + e.getMessage());
        }
    }

    private String createPost(String username, String content) {

        User user = userRepository.getUserByUsername(username);
        if (user == null) {
            return createErrorResponse("Usuário não encontrado");
        }

        try {

            String postId = UUID.randomUUID().toString();


            Post post = new Post(postId, username, content);
            boolean success = postRepository.addPost(post);

            if (success) {
                logger.log("Post criado com sucesso: " + postId + " por " + username);

                ReplicationManager.getInstance().registerPostCreation(post);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Post criado com sucesso");
                response.put("postId", post.getId());
                response.put("username", post.getUsername());
                response.put("createdAt", post.getCreatedAt().toString());
                return response.toString();
            } else {
                return createErrorResponse("Falha ao criar post");
            }
        } catch (Exception e) {
            logger.logError("Erro ao criar post", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String updatePost(String postId, String username, String content) {

        Post post = postRepository.getPostById(postId);
        if (post == null) {
            return createErrorResponse("Post não encontrado");
        }

        if (!post.getUsername().equals(username)) {
            return createErrorResponse("Apenas o autor pode atualizar o post");
        }

        try {
            post.setContent(content);
            boolean success = postRepository.updatePost(post);

            if (success) {
                logger.log("Post atualizado com sucesso: " + postId);

                ReplicationManager.getInstance().registerPostUpdate(post);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Post atualizado com sucesso");
                response.put("postId", post.getId());
                response.put("updatedAt", post.getUpdatedAt().toString());
                return response.toString();
            } else {
                return createErrorResponse("Falha ao atualizar post");
            }
        } catch (Exception e) {
            logger.logError("Erro ao atualizar post", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String deletePost(String postId, String username) {
        Post post = postRepository.getPostById(postId);
        if (post == null) {
            return createErrorResponse("Post não encontrado");
        }

        if (!post.getUsername().equals(username)) {
            return createErrorResponse("Apenas o autor pode remover o post");
        }

        try {
            boolean success = postRepository.removePost(postId);

            if (success) {
                logger.log("Post removido com sucesso: " + postId);

                ReplicationManager.getInstance().registerPostDeletion(postId);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Post removido com sucesso");
                return response.toString();
            } else {
                return createErrorResponse("Falha ao remover post");
            }
        } catch (Exception e) {
            logger.logError("Erro ao remover post", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String getUserPosts(String username) {
        User user = userRepository.getUserByUsername(username);
        if (user == null) {
            return createErrorResponse("Usuário não encontrado");
        }

        try {

            List<Post> posts = postRepository.getPostsByUsername(username);

            JSONObject response = new JSONObject();
            response.put("success", true);

            JSONArray postsArray = new JSONArray();
            for (Post post : posts) {
                JSONObject postJson = new JSONObject();
                postJson.put("id", post.getId());
                postJson.put("username", post.getUsername());
                postJson.put("content", post.getContent());
                postJson.put("createdAt", post.getCreatedAt().toString());
                postJson.put("updatedAt", post.getUpdatedAt().toString());
                postsArray.put(postJson);
            }

            response.put("posts", postsArray);
            response.put("count", posts.size());

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao buscar posts do usuário", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String getFeed(String username, int limit) {
        User user = userRepository.getUserByUsername(username);
        if (user == null) {
            return createErrorResponse("Usuário não encontrado");
        }

        try {
            List<String> following = new java.util.ArrayList<>(user.getFollowing());
            following.add(username);

            List<Post> feedPosts = postRepository.getRecentPostsByUsers(following, limit);

            JSONObject response = new JSONObject();
            response.put("success", true);

            JSONArray postsArray = new JSONArray();
            for (Post post : feedPosts) {
                JSONObject postJson = new JSONObject();
                postJson.put("id", post.getId());
                postJson.put("username", post.getUsername());
                postJson.put("content", post.getContent());
                postJson.put("createdAt", post.getCreatedAt().toString());
                postJson.put("updatedAt", post.getUpdatedAt().toString());
                postsArray.put(postJson);
            }

            response.put("posts", postsArray);
            response.put("count", feedPosts.size());

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao buscar feed do usuário", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String createErrorResponse(String message) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("error", message);
        return response.toString();
    }
}