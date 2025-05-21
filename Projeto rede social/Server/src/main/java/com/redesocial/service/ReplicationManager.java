package com.redesocial.service;

import com.redesocial.model.*;
import com.redesocial.repository.MessageRepository;
import com.redesocial.repository.PostRepository;
import com.redesocial.repository.UserRepository;
import com.redesocial.util.EventLogger;
import org.json.JSONObject;

public class ReplicationManager {
    private static ReplicationManager instance;
    private ServerState serverState;
    private EventLogger logger;
    private UserRepository userRepository;
    private DataReplicationService replicationService;
    private PostRepository postRepository;
    private MessageRepository messageRepository;

    private ReplicationManager() {
    }

    public static synchronized ReplicationManager getInstance() {
        if (instance == null) {
            instance = new ReplicationManager();
        }
        return instance;
    }

    public void initialize(ServerState serverState, EventLogger logger,
                           UserRepository userRepository,
                           PostRepository postRepository,
                           MessageRepository messageRepository,
                           DataReplicationService replicationService) {
        this.serverState = serverState;
        this.logger = logger;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.messageRepository = messageRepository;
        this.replicationService = replicationService;
        logger.log("ReplicationManager inicializado com todos os repositórios");
    }

    public void registerUserCreation(User user) {
        if (replicationService == null) {
            logger.log("Serviço de replicação não inicializado");
            return;
        }

        JSONObject userData = new JSONObject();
        userData.put("username", user.getUsername());
        userData.put("password", user.getPassword());
        userData.put("createdAt", user.getCreatedAt().toString());

        ReplicationEvent event = new ReplicationEvent(
                ReplicationEvent.TYPE_USER_CREATED,
                user.getUsername(),
                System.currentTimeMillis(),
                userData
        );

        replicationService.queueReplicationEvent(event);
    }

    public void registerFollowAdded(String username, String followerUsername) {
        if (replicationService == null) {
            logger.log("Serviço de replicação não inicializado");
            return;
        }

        JSONObject followData = new JSONObject();
        followData.put("username", username);
        followData.put("followerUsername", followerUsername);

        ReplicationEvent event = new ReplicationEvent(
                ReplicationEvent.TYPE_FOLLOW_ADDED,
                username + "_" + followerUsername,
                System.currentTimeMillis(),
                followData
        );

        replicationService.queueReplicationEvent(event);
    }

    public void registerFollowRemoved(String username, String followerUsername) {
        if (replicationService == null) {
            logger.log("Serviço de replicação não inicializado");
            return;
        }

        JSONObject followData = new JSONObject();
        followData.put("username", username);
        followData.put("followerUsername", followerUsername);

        ReplicationEvent event = new ReplicationEvent(
                ReplicationEvent.TYPE_FOLLOW_REMOVED,
                username + "_" + followerUsername,
                System.currentTimeMillis(),
                followData
        );

        replicationService.queueReplicationEvent(event);
    }

    public void handleReplicationEvent(String eventType, String entityId,
                                       long timestamp, JSONObject data) {
        logger.log("Processando evento de replicação: " + eventType + " para " + entityId);

        switch (eventType) {
            case ReplicationEvent.TYPE_USER_CREATED:
                replicateUserCreation(data);
                break;
            case ReplicationEvent.TYPE_FOLLOW_ADDED:
                replicateFollowAdded(data);
                break;
            case ReplicationEvent.TYPE_FOLLOW_REMOVED:
                replicateFollowRemoved(data);
                break;
            case ReplicationEvent.TYPE_POST_CREATED:
                replicatePostCreation(data);
                break;
            case ReplicationEvent.TYPE_POST_UPDATED:
                replicatePostUpdate(data);
                break;
            case ReplicationEvent.TYPE_POST_DELETED:
                replicatePostDeletion(data);
                break;
            case ReplicationEvent.TYPE_MESSAGE_SENT:
                replicateMessageSent(data);
                break;
            default:
                logger.log("Tipo de evento de replicação não reconhecido: " + eventType);
        }
    }

    private void replicateUserCreation(JSONObject userData) {
        try {
            String username = userData.getString("username");
            String password = userData.getString("password");

            if (userRepository.getUserByUsername(username) != null) {
                logger.log("Usuário " + username + " já existe, ignorando replicação");
                return;
            }

            User user = new User(username, password);
            boolean success = userRepository.addUser(user);

            if (success) {
                logger.log("Usuário " + username + " replicado com sucesso");
            } else {
                logger.logError("Falha ao replicar usuário " + username, null);
            }
        } catch (Exception e) {
            logger.logError("Erro ao replicar criação de usuário", e);
        }
    }

    private void replicateFollowAdded(JSONObject followData) {
        try {
            String username = followData.getString("username");
            String followerUsername = followData.getString("followerUsername");

            User user = userRepository.getUserByUsername(username);
            User follower = userRepository.getUserByUsername(followerUsername);

            if (user == null || follower == null) {
                logger.logError("Usuário não encontrado para replicação de seguidor", null);
                return;
            }

            user.addFollower(followerUsername);
            follower.addFollowing(username);

            userRepository.updateUser(user);
            userRepository.updateUser(follower);

            logger.log("Relação de seguidor adicionada: " + followerUsername + " segue " + username);
        } catch (Exception e) {
            logger.logError("Erro ao replicar adição de seguidor", e);
        }
    }

    private void replicateFollowRemoved(JSONObject followData) {
        try {
            String username = followData.getString("username");
            String followerUsername = followData.getString("followerUsername");

            User user = userRepository.getUserByUsername(username);
            User follower = userRepository.getUserByUsername(followerUsername);

            if (user == null || follower == null) {
                logger.logError("Usuário não encontrado para replicação de remoção de seguidor", null);
                return;
            }

            user.removeFollower(followerUsername);
            follower.removeFollowing(username);

            userRepository.updateUser(user);
            userRepository.updateUser(follower);

            logger.log("Relação de seguidor removida: " + followerUsername + " não segue mais " + username);
        } catch (Exception e) {
            logger.logError("Erro ao replicar remoção de seguidor", e);
        }
    }

    public void registerPostCreation(Post post) {
        if (replicationService == null) {
            logger.log("Serviço de replicação não inicializado");
            return;
        }

        JSONObject postData = new JSONObject();
        postData.put("id", post.getId());
        postData.put("username", post.getUsername());
        postData.put("content", post.getContent());
        postData.put("createdAt", post.getCreatedAt().toString());
        postData.put("updatedAt", post.getUpdatedAt().toString());

        ReplicationEvent event = new ReplicationEvent(
                ReplicationEvent.TYPE_POST_CREATED,
                post.getId(),
                System.currentTimeMillis(),
                postData
        );

        replicationService.queueReplicationEvent(event);
    }

    public void registerPostUpdate(Post post) {
        if (replicationService == null) {
            logger.log("Serviço de replicação não inicializado");
            return;
        }

        JSONObject postData = new JSONObject();
        postData.put("id", post.getId());
        postData.put("content", post.getContent());
        postData.put("updatedAt", post.getUpdatedAt().toString());

        ReplicationEvent event = new ReplicationEvent(
                ReplicationEvent.TYPE_POST_UPDATED,
                post.getId(),
                System.currentTimeMillis(),
                postData
        );

        replicationService.queueReplicationEvent(event);
    }

    public void registerPostDeletion(String postId) {
        if (replicationService == null) {
            logger.log("Serviço de replicação não inicializado");
            return;
        }

        JSONObject postData = new JSONObject();
        postData.put("id", postId);

        ReplicationEvent event = new ReplicationEvent(
                ReplicationEvent.TYPE_POST_DELETED,
                postId,
                System.currentTimeMillis(),
                postData
        );

        replicationService.queueReplicationEvent(event);
    }

    public void registerMessageSent(Message message) {
        if (replicationService == null) {
            logger.log("Serviço de replicação não inicializado");
            return;
        }

        JSONObject messageData = new JSONObject();
        messageData.put("id", message.getId());
        messageData.put("senderUsername", message.getSenderUsername());
        messageData.put("receiverUsername", message.getReceiverUsername());
        messageData.put("content", message.getContent());
        messageData.put("sentAt", message.getSentAt().toString());
        messageData.put("read", message.isRead());
        if (message.getReadAt() != null) {
            messageData.put("readAt", message.getReadAt().toString());
        }

        ReplicationEvent event = new ReplicationEvent(
                ReplicationEvent.TYPE_MESSAGE_SENT,
                message.getId(),
                System.currentTimeMillis(),
                messageData
        );

        replicationService.queueReplicationEvent(event);
    }

    private void replicatePostCreation(JSONObject postData) {
        try {
            String id = postData.getString("id");
            String username = postData.getString("username");
            String content = postData.getString("content");
            String createdAtStr = postData.getString("createdAt");
            String updatedAtStr = postData.getString("updatedAt");

            if (postRepository.getPostById(id) != null) {
                logger.log("Post " + id + " já existe, ignorando replicação");
                return;
            }

            Post post = new Post(id, username, content);

            boolean success = postRepository.addPost(post);

            if (success) {
                logger.log("Post " + id + " replicado com sucesso");
            } else {
                logger.logError("Falha ao replicar post " + id, null);
            }
        } catch (Exception e) {
            logger.logError("Erro ao replicar criação de post", e);
        }
    }

    private void replicatePostUpdate(JSONObject postData) {
        try {
            String id = postData.getString("id");
            String content = postData.getString("content");
            String updatedAtStr = postData.getString("updatedAt");

            Post post = postRepository.getPostById(id);

            if (post == null) {
                logger.logError("Post não encontrado para atualização: " + id, null);
                return;
            }

            post.setContent(content);

            boolean success = postRepository.updatePost(post);

            if (success) {
                logger.log("Post " + id + " atualizado com sucesso");
            } else {
                logger.logError("Falha ao atualizar post " + id, null);
            }
        } catch (Exception e) {
            logger.logError("Erro ao replicar atualização de post", e);
        }
    }

    private void replicatePostDeletion(JSONObject postData) {
        try {
            String id = postData.getString("id");

            boolean success = postRepository.removePost(id);

            if (success) {
                logger.log("Post " + id + " removido com sucesso");
            } else {
                logger.logError("Falha ao remover post " + id, null);
            }
        } catch (Exception e) {
            logger.logError("Erro ao replicar remoção de post", e);
        }
    }

    private void replicateMessageSent(JSONObject messageData) {
        try {
            String id = messageData.getString("id");
            String senderUsername = messageData.getString("senderUsername");
            String receiverUsername = messageData.getString("receiverUsername");
            String content = messageData.getString("content");
            String sentAtStr = messageData.getString("sentAt");
            boolean read = messageData.getBoolean("read");

            if (messageRepository.getMessageById(id) != null) {
                logger.log("Mensagem " + id + " já existe, ignorando replicação");
                return;
            }

            Message message = new Message(id, senderUsername, receiverUsername, content);
            if (read && messageData.has("readAt")) {
                message.markAsRead();
            }

            boolean success = messageRepository.addMessage(message);

            if (success) {
                logger.log("Mensagem " + id + " replicada com sucesso");
            } else {
                logger.logError("Falha ao replicar mensagem " + id, null);
            }
        } catch (Exception e) {
            logger.logError("Erro ao replicar envio de mensagem", e);
        }
    }
}