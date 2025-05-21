package com.redesocial.service;

import com.redesocial.model.User;
import com.redesocial.repository.UserRepository;
import com.redesocial.util.EventLogger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FollowService {
    private final UserRepository userRepository;
    private final EventLogger logger;
    private final String bindAddress;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    public FollowService(UserRepository userRepository, EventLogger logger, String address, int port) {
        this.userRepository = userRepository;
        this.logger = logger;
        this.bindAddress = "tcp://" + address + ":" + (port + 200); // Porta diferente dos outros serviços
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
            logger.log("Serviço de seguidores parado");
        }
    }

    private void runService() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind(bindAddress);

            logger.log("Serviço de seguidores iniciado em " + bindAddress);

            while (running.get()) {
                byte[] request = socket.recv();
                if (request == null) {
                    continue;
                }

                String requestStr = new String(request, StandardCharsets.UTF_8);
                logger.log("Requisição recebida no serviço de seguidores: " + requestStr);

                String response = processRequest(requestStr);

                socket.send(response.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de seguidores", e);
        }
    }

    private String processRequest(String requestStr) {
        try {
            JSONObject request = new JSONObject(requestStr);
            String action = request.getString("action");

            switch (action) {
                case "FOLLOW_USER":
                    return followUser(
                            request.getString("followerUsername"),
                            request.getString("followedUsername")
                    );
                case "UNFOLLOW_USER":
                    return unfollowUser(
                            request.getString("followerUsername"),
                            request.getString("followedUsername")
                    );
                case "GET_FOLLOWERS":
                    return getFollowers(
                            request.getString("username")
                    );
                case "GET_FOLLOWING":
                    return getFollowing(
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

    private String followUser(String followerUsername, String followedUsername) {

        User follower = userRepository.getUserByUsername(followerUsername);
        User followed = userRepository.getUserByUsername(followedUsername);

        if (follower == null) {
            return createErrorResponse("Seguidor não encontrado");
        }

        if (followed == null) {
            return createErrorResponse("Usuário a ser seguido não encontrado");
        }

        if (follower.getFollowing().contains(followedUsername)) {
            return createErrorResponse("Já está seguindo este usuário");
        }

        if (followerUsername.equals(followedUsername)) {
            return createErrorResponse("Não é possível seguir a si mesmo");
        }

        try {

            follower.addFollowing(followedUsername);
            followed.addFollower(followerUsername);

            boolean success1 = userRepository.updateUser(follower);
            boolean success2 = userRepository.updateUser(followed);

            if (success1 && success2) {
                logger.log("Usuário " + followerUsername + " agora está seguindo " + followedUsername);

                ReplicationManager.getInstance().registerFollowAdded(followedUsername, followerUsername);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Agora está seguindo " + followedUsername);
                return response.toString();
            } else {
                return createErrorResponse("Falha ao seguir usuário");
            }
        } catch (Exception e) {
            logger.logError("Erro ao seguir usuário", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String unfollowUser(String followerUsername, String followedUsername) {

        User follower = userRepository.getUserByUsername(followerUsername);
        User followed = userRepository.getUserByUsername(followedUsername);

        if (follower == null) {
            return createErrorResponse("Seguidor não encontrado");
        }

        if (followed == null) {
            return createErrorResponse("Usuário a ser deixado de seguir não encontrado");
        }

        if (!follower.getFollowing().contains(followedUsername)) {
            return createErrorResponse("Não está seguindo este usuário");
        }

        try {

            follower.removeFollowing(followedUsername);
            followed.removeFollower(followerUsername);

            boolean success1 = userRepository.updateUser(follower);
            boolean success2 = userRepository.updateUser(followed);

            if (success1 && success2) {
                logger.log("Usuário " + followerUsername + " deixou de seguir " + followedUsername);

                ReplicationManager.getInstance().registerFollowRemoved(followedUsername, followerUsername);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Deixou de seguir " + followedUsername);
                return response.toString();
            } else {
                return createErrorResponse("Falha ao deixar de seguir usuário");
            }
        } catch (Exception e) {
            logger.logError("Erro ao deixar de seguir usuário", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String getFollowers(String username) {

        User user = userRepository.getUserByUsername(username);
        if (user == null) {
            return createErrorResponse("Usuário não encontrado");
        }

        try {

            Set<String> followers = user.getFollowers();

            JSONObject response = new JSONObject();
            response.put("success", true);

            JSONArray followersArray = new JSONArray();
            for (String follower : followers) {
                followersArray.put(follower);
            }

            response.put("followers", followersArray);
            response.put("count", followers.size());

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao buscar seguidores", e);
            return createErrorResponse("Erro interno: " + e.getMessage());
        }
    }

    private String getFollowing(String username) {

        User user = userRepository.getUserByUsername(username);
        if (user == null) {
            return createErrorResponse("Usuário não encontrado");
        }

        try {

            Set<String> following = user.getFollowing();

            JSONObject response = new JSONObject();
            response.put("success", true);

            JSONArray followingArray = new JSONArray();
            for (String followed : following) {
                followingArray.put(followed);
            }

            response.put("following", followingArray);
            response.put("count", following.size());

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao buscar usuários seguidos", e);
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