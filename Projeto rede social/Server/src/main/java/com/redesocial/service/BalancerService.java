package com.redesocial.service;

import com.redesocial.server.LoadBalancer;
import com.redesocial.util.EventLogger;

import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BalancerService {
    private final LoadBalancer loadBalancer;
    private final EventLogger logger;
    private final String bindAddress;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    public BalancerService(LoadBalancer loadBalancer, EventLogger logger, String address, int port) {
        this.loadBalancer = loadBalancer;
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
            logger.log("Serviço de balanceamento parado");
        }
    }

    private void runService() {
        try (ZContext context = new ZContext()) {
            // Socket para receber requisições dos clientes
            ZMQ.Socket frontendSocket = context.createSocket(SocketType.ROUTER);
            frontendSocket.bind(bindAddress);

            // Socket para enviar requisições para os servidores
            ZMQ.Socket backendSocket = context.createSocket(SocketType.DEALER);

            logger.log("Serviço de balanceamento iniciado em: " + bindAddress);

            while (running.get()) {
                // Aguarda uma requisição
                byte[] identity = frontendSocket.recv();
                if (identity == null) continue;

                byte[] empty = frontendSocket.recv(); // Recebe o frame vazio
                byte[] requestBytes = frontendSocket.recv();

                if (requestBytes == null) continue;

                String requestStr = new String(requestBytes, StandardCharsets.UTF_8);
                logger.log("Requisição recebida no balanceador: " + requestStr);

                // Seleciona um servidor usando Round Robin
                LoadBalancer.ServerInfo server = loadBalancer.getNextServer();
                if (server == null) {
                    // Responde ao cliente que não há servidores disponíveis
                    String errorResponse = createErrorResponse("Nenhum servidor disponível");
                    frontendSocket.send(identity, ZMQ.SNDMORE);
                    frontendSocket.send(empty, ZMQ.SNDMORE);
                    frontendSocket.send(errorResponse.getBytes(StandardCharsets.UTF_8));
                    continue;
                }

                // Conecta-se ao servidor selecionado
                String serverAddress = "tcp://" + server.getAddress() + ":" + server.getPort();
                try (ZContext serverContext = new ZContext()) {
                    ZMQ.Socket serverSocket = serverContext.createSocket(SocketType.REQ);
                    serverSocket.connect(serverAddress);

                    // Envia a requisição para o servidor
                    serverSocket.send(requestBytes);
                    logger.log("Requisição encaminhada para o servidor: " + serverAddress);

                    // Recebe a resposta do servidor
                    byte[] responseBytes = serverSocket.recv();
                    if (responseBytes == null) {
                        String errorResponse = createErrorResponse("Erro na comunicação com o servidor");
                        frontendSocket.send(identity, ZMQ.SNDMORE);
                        frontendSocket.send(empty, ZMQ.SNDMORE);
                        frontendSocket.send(errorResponse.getBytes(StandardCharsets.UTF_8));
                        continue;
                    }

                    // Envia a resposta de volta para o cliente
                    frontendSocket.send(identity, ZMQ.SNDMORE);
                    frontendSocket.send(empty, ZMQ.SNDMORE);
                    frontendSocket.send(responseBytes);

                    logger.log("Resposta enviada ao cliente");
                }
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de balanceamento", e);
        }
    }

    private String createErrorResponse(String message) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("error", message);
        return response.toString();
    }
}