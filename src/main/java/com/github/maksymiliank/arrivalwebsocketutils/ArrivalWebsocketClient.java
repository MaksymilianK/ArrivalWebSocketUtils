package com.github.maksymiliank.arrivalwebsocketutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class ArrivalWebsocketClient extends WebSocketClient {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Logger logger;
    private final Gson gson;
    private final Map<Integer, List<Consumer<InboundMessage>>> listeners = new HashMap<>();

    public ArrivalWebsocketClient(URI serverUri, Logger logger) {
        super(serverUri);

        this.logger = logger;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Integer.class, new InboundMessageDeserializer())
                .create();
    }

    public void addListener(int messageType, Consumer<InboundMessage> onMessage) {
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

    public void send(OutboundMessage message) {
        this.send(gson.toJson(message));
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        logger.info("Opened WebSocket connection to the server");
    }

    @Override
    public void onMessage(String rawMessage) {
        var message = gson.fromJson(rawMessage, InboundMessage.class);

        lock.readLock().lock();
        try {
            if (listeners.containsKey(message.getType())) {
                listeners.get(message.getType()).forEach(c -> c.accept(message));
            } else {
                logger.warn("There is no registered listener for client message type {}", message.getType());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Connection from the server has been closed");
    }

    @Override
    public void onError(Exception e) {
        logger.error(e.getMessage());
    }
}
