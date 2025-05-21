package com.redesocial.server;

import com.redesocial.clock.SynchronizationManager;
import com.redesocial.model.ServerState;
import com.redesocial.repository.MessageRepository;
import com.redesocial.repository.PostRepository;
import com.redesocial.repository.UserRepository;
import com.redesocial.service.BalancerService;
import com.redesocial.service.DataReplicationService;
import com.redesocial.service.FollowService;
import com.redesocial.service.MessageService;
import com.redesocial.service.PostService;
import com.redesocial.service.ReplicationManager;
import com.redesocial.service.UserService;
import com.redesocial.util.EventLogger;

import java.io.File;
import java.io.IOException;

public class Main {
    private ServerConfig config;
    private ServerState serverState;
    private EventLogger logger;

    // Repositórios
    private UserRepository userRepository;
    private PostRepository postRepository;
    private MessageRepository messageRepository;

    // Serviços
    private UserService userService;
    private PostService postService;
    private MessageService messageService;
    private FollowService followService;

    // Balanceador e sincronização
    private LoadBalancer loadBalancer;
    private BalancerService balancerService;
    private SynchronizationManager syncManager;

    private boolean isBalancer;

    public void initialize(String configFile) throws IOException {
        config = new ServerConfig(configFile);
        System.out.println("Starting server with configuration: " + config);

        isBalancer = Boolean.parseBoolean(config.getProperty("is.balancer", "false"));

        File dataDir = new File(config.getDataDirectory());
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        serverState = config.createServerState();
        logger = new EventLogger(serverState, config.getLogFilePath());

        syncManager = new SynchronizationManager(serverState, logger);
        syncManager.initialize(config);

        if (isBalancer) {
            initializeBalancer();
        } else {
            initializeServer();
        }
    }

    private void initializeBalancer() {
        logger.log("Inicializando como balanceador de carga");

        loadBalancer = new LoadBalancer(logger);

        String[] seedServers = config.getSeedServers();
        for (String serverAddress : seedServers) {
            String[] parts = serverAddress.split(":");
            if (parts.length == 3) {
                String serverId = parts[0];
                String address = parts[1];

                int port;

                if (serverId.equals("server1")) {
                    port = 5555;
                } else if (serverId.equals("server2")) {
                    port = 5556;
                } else if (serverId.equals("server3")) {
                    port = 5557;
                } else {
                    port = Integer.parseInt(parts[2]);
                    logger.log("Aviso: Usando porta de sincronização para o servidor " + serverId +
                            ". Isso pode causar problemas de comunicação!");
                }

                loadBalancer.addServer(serverId, address, port);
                logger.log("Adicionado servidor " + serverId + " com porta de serviço " + port);
            }
        }

        int balancerPort = Integer.parseInt(config.getProperty("balancer.port", "5000"));
        balancerService = new BalancerService(loadBalancer, logger, config.getServerAddress(), balancerPort);
    }

    private void initializeServer() {
        String userDataDir = config.getProperty("user.data.directory", "./user_data");
        String postDataDir = config.getProperty("post.data.directory", "./post_data");
        String messageDataDir = config.getProperty("message.data.directory", "./message_data");

        createDirectoryIfNotExists(userDataDir);
        createDirectoryIfNotExists(postDataDir);
        createDirectoryIfNotExists(messageDataDir);

        String serverId = serverState.getServerId();
        String userDataPath = userDataDir + "/users_" + serverId + ".data";
        String postDataPath = postDataDir + "/posts_" + serverId + ".data";
        String messageDataPath = messageDataDir + "/messages_" + serverId + ".data";

        userRepository = new UserRepository(userDataPath, logger);
        postRepository = new PostRepository(postDataPath, logger);
        messageRepository = new MessageRepository(messageDataPath, logger);

        int serviceBasePort = Integer.parseInt(config.getProperty("user.service.port", "5555"));

        userService = new UserService(userRepository, logger, config.getServerAddress(), serviceBasePort);
        postService = new PostService(postRepository, userRepository, logger, config.getServerAddress(), serviceBasePort);
        messageService = new MessageService(messageRepository, userRepository, logger, config.getServerAddress(), serviceBasePort);
        followService = new FollowService(userRepository, logger, config.getServerAddress(), serviceBasePort);

    }

    private void createDirectoryIfNotExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public void start() {
        syncManager.start();

        if (isBalancer) {
            balancerService.start();
            logger.log("Balanceador de carga iniciado");
        } else {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            DataReplicationService replicationService = syncManager.getReplicationService();

            if (replicationService != null) {
                ReplicationManager.getInstance().initialize(
                        serverState, logger, userRepository, postRepository,
                        messageRepository, replicationService
                );
            } else {
                logger.logError("ERRO: DataReplicationService é null!", null);
            }

            // Inicia todos os serviços no servidor de aplicação
            userService.start();
            postService.start();
            messageService.start();
            followService.start();

            logger.log("Todos os serviços de aplicação iniciados");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        logger.log("Sistema iniciado com sucesso");
    }

    public void shutdown() {
        logger.log("Desligando servidor...");

        if (syncManager != null) {
            syncManager.stop();
        }

        if (isBalancer) {
            if (balancerService != null) {
                balancerService.stop();
            }
        } else {
            if (userService != null) {
                userService.stop();
            }
            if (postService != null) {
                postService.stop();
            }
            if (messageService != null) {
                messageService.stop();
            }
            if (followService != null) {
                followService.stop();
            }
        }

        if (logger != null) {
            logger.close();
        }

        System.out.println("Servidor desligado com sucesso");
    }

    public static void main(String[] args) {
        try {
            String configFile = args.length > 0 ? args[0] : "server1.properties";

            Main server = new Main();
            server.initialize(configFile);
            server.start();

            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}