package com.github.maksymiliank.arrivalwebsocketutils;

import com.github.maksymiliank.arrivalwebsocketutils.message.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class ArrivalWebsocketServer extends WebSocketServer {

    public static final int CODE_UNAUTHORIZED = 4000;
    public static final int CODE_ALREADY_CONNECTED = 4001;

    private static final int CLIENT_REGISTRATION_TIME = 5000;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ExecutorService waitingClientsExecutor = Executors.newCachedThreadPool();

    private final Logger logger;
    private final Gson gson;
    private final Set<String> allowedClientHosts;
    private final Set<WebSocket> waitingClients = new HashSet<>();
    private final Map<String, WebSocket> connectedClients = new HashMap<>();
    private final Map<Integer, List<Consumer<JsonObject>>> listeners = new HashMap<>();

    public ArrivalWebsocketServer(int port, Logger logger, Collection<String> allowedClientHosts, Gson gson) {
        super(new InetSocketAddress("localhost", port));

        this.logger = logger;
        this.gson = gson;
        this.allowedClientHosts = Set.copyOf(allowedClientHosts);
    }

    public void addListener(int messageType, Consumer<JsonObject> onMessage) {
        lock.writeLock().lock();
        try {
            if (!listeners.containsKey(messageType)) {
                listeners.put(messageType, new ArrayList<>());
            }

            listeners.get(messageType).add(onMessage);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean send(String client, Message message) {
        lock.readLock().lock();
        try {
            if (!connectedClients.containsKey(client)) {
                return false;
            }

            connectedClients.get(client).send(gson.toJson(message));
        } finally {
            lock.readLock().unlock();
        }

        return true;
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket connection, Draft draft,
                                                                       ClientHandshake request) throws InvalidDataException {
        ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(connection, draft, request);

        String client = getClientHost(connection);
        if (!allowedClientHosts.contains(client)) {
            throw new InvalidDataException(
                    CODE_UNAUTHORIZED,
                    String.format("Connection from the host '%s' is not allowed", client)
            );
        }

        return builder;
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        lock.writeLock().lock();
        try {
            waitingClients.add(connection);
            waitingClientsExecutor.execute(() -> removeWaitingClientIfNotRegistered(connection));
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Opened connection from client {}", getClientHost(connection));
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        lock.writeLock().lock();
        try {
            if (waitingClients.contains(connection)) {
                waitingClients.remove(connection);
                logger.info("Connection from the waiting client has been closed");
            } else {
                String clientName = removeConnectedClient(connection);
                logger.info("Connection from the client {} has been closed", clientName);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void onMessage(WebSocket connection, String rawMessage) {
        var message = gson.fromJson(rawMessage, RawMessage.class);

        if (message.type() == InboundMessageType.CLIENT_REGISTRATION.getType()) {
            addConnectedClient(connection, gson.fromJson(message.body(), ClientRegistrationInboundMessage.class));
        } else {
            callListeners(message);
        }
    }

    @Override
    public void onError(WebSocket connection, Exception e) {
        logger.error(e.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server is running");
    }

    private void removeWaitingClientIfNotRegistered(WebSocket connection) {
        try {
            Thread.sleep(CLIENT_REGISTRATION_TIME);
        } catch (InterruptedException e) {
            logger.warn("Executor interrupted");
        }

        lock.writeLock().lock();
        try {
            if (waitingClients.contains(connection)) {
                connection.close(CODE_UNAUTHORIZED, "Client has not registered");
                waitingClients.remove(connection);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String removeConnectedClient(WebSocket connection) {
        String clientName = connectedClients.entrySet().stream()
                .filter(e -> e.getValue().equals(connection))
                .map(Map.Entry::getKey)
                .findAny().orElseThrow(() -> new RuntimeException("Cannot find client"));

        connectedClients.remove(clientName);
        return clientName;
    }

    private void addConnectedClient(WebSocket connection, ClientRegistrationInboundMessage message) {
        lock.writeLock().lock();
        try {
            waitingClients.remove(connection);
            connectedClients.put(message.getClientName(), connection);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void callListeners(RawMessage message) {
        lock.readLock().lock();
        try {
            if (listeners.containsKey(message.type())) {
                listeners.get(message.type()).forEach(l -> l.accept(message.body()));
            } else {
                logger.warn("There is no registered listener for server message type {}", message.type());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private static String getClientHost(WebSocket connection) {
        return connection.getRemoteSocketAddress().getHostString();
    }
}
