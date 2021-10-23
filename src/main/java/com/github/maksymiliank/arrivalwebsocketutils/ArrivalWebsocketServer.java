package com.github.maksymiliank.arrivalwebsocketutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

public class ArrivalWebsocketServer extends WebSocketServer {

    public static final int CODE_UNAUTHORIZED = 4000;
    public static final int CODE_ALREADY_CONNECTED = 4001;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Logger logger;
    private final Gson gson;
    private final Map<InetSocketAddress, Integer> allowedClientAddresses = new HashMap<>();
    private final Map<Integer, WebSocket> connectedClients = new HashMap<>();
    private final Map<Integer, List<BiConsumer<Integer, InboundMessage>>> listeners = new HashMap<>();

    public ArrivalWebsocketServer(int port, Logger logger, Map<InetSocketAddress, Integer> allowedClientAddresses) {
        super(new InetSocketAddress("localhost", port));

        this.logger = logger;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Integer.class, new InboundMessageDeserializer())
                .create();
        this.allowedClientAddresses.putAll(allowedClientAddresses);
    }

    public void addListener(int messageType, BiConsumer<Integer, InboundMessage> onMessage) {
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

    public boolean send(int clientId, OutboundMessage message) {
        lock.readLock().lock();
        try {
            if (!connectedClients.containsKey(clientId)) {
                return false;
            }

            connectedClients.get(clientId).send(gson.toJson(message));
        } finally {
            lock.readLock().unlock();
        }

        return true;
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket connection, Draft draft,
                                                                       ClientHandshake request) throws InvalidDataException {
        ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(connection, draft, request);

        if (!allowedClientAddresses.containsKey(connection.getRemoteSocketAddress())) {
            throw new InvalidDataException(
                    CODE_UNAUTHORIZED,
                    String.format("Connection from the address '%s' is not allowed", connection.getRemoteSocketAddress())
            );
        }

        int clientId = allowedClientAddresses.get(connection.getRemoteSocketAddress());

        lock.readLock().lock();
        try {
            if (connectedClients.containsKey(clientId)) {
                throw new InvalidDataException(
                        CODE_ALREADY_CONNECTED,
                        String.format("The client %d is not allowed", clientId)
                );
            }
            return builder;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        int clientId = getClientId(connection);

        lock.writeLock().lock();
        try {
            connectedClients.put(clientId, connection);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Opened connection from client {}", clientId);
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        int clientId = getClientId(connection);

        lock.writeLock().lock();
        try {
            connectedClients.remove(clientId);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Connection from the client {} has been closed", clientId);
    }

    @Override
    public void onMessage(WebSocket connection, String rawMessage) {
        int clientId = getClientId(connection);
        var message = gson.fromJson(rawMessage, InboundMessage.class);

        lock.readLock().lock();
        try {
            if (listeners.containsKey(message.getType())) {
                listeners.get(message.getType()).forEach(c -> c.accept(clientId, message));
            } else {
                logger.warn("There is no registered listener for server message type {}", message.getType());
            }
        } finally {
            lock.readLock().unlock();
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

    private int getClientId(WebSocket connection) {
        return allowedClientAddresses.get(connection.getRemoteSocketAddress());
    }
}
