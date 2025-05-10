package com.redesocial.service;

import com.redesocial.model.User;
import com.redesocial.repository.UserRepository;
import com.redesocial.util.EventLogger;
import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserService {
    private final UserRepository userRepository;
    private final EventLogger logger;
    private final String bindAddress;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    public UserService(UserRepository userRepository, EventLogger logger, String address, int port) {
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
            logger.log("Serviço de usuários parado");
        }
    }

    private void runService() {
        try (ZContext context = new ZContext()) {
            // Socket REP para responder às requisições
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind(bindAddress);

            while (running.get()) {
                // Aguarda uma requisição
                byte[] request = socket.recv();
                if (request == null) {
                    continue;
                }

                String requestStr = new String(request, StandardCharsets.UTF_8);
                logger.log("Requisição recebida: " + requestStr);

                // Processa a requisição
                String response = processRequest(requestStr);

                // Envia a resposta
                socket.send(response.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de usuários", e);
        }
    }

    private String processRequest(String requestStr) {
        try {
            JSONObject request = new JSONObject(requestStr);
            String action = request.getString("action");

            switch (action) {
                case "register":
                    return registerUser(
                            request.getString("username"),
                            request.getString("password")
                    );
                default:
                    return createErrorResponse("Ação desconhecida: " + action);
            }
        } catch (Exception e) {
            logger.logError("Erro ao processar requisição", e);
            return createErrorResponse("Erro ao processar requisição: " + e.getMessage());
        }
    }

    private String registerUser(String username, String password) {
        // Verificar se o usuário já existe
        if (userRepository.getUserByUsername(username) != null) {
            return createErrorResponse("Nome de usuário já está em uso");
        }

        try {
            // Criar e salvar o usuário
            User user = new User(username, password);
            boolean success = userRepository.addUser(user);

            if (success) {
                logger.log("Usuário registrado com sucesso: " + username);

                // Registra o evento para replicação
                ReplicationManager.getInstance().registerUserCreation(user);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("message", "Usuário registrado com sucesso");
                response.put("username", user.getUsername());
                return response.toString();
            } else {
                return createErrorResponse("Falha ao registrar usuário");
            }
        } catch (Exception e) {
            logger.logError("Erro ao registrar usuário", e);
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