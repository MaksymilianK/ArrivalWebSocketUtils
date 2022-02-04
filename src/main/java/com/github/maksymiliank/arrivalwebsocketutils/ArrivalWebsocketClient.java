package com.github.maksymiliank.arrivalwebsocketutils;

import com.github.maksymiliank.arrivalwebsocketutils.message.Message;
import com.github.maksymiliank.arrivalwebsocketutils.message.RawMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class ArrivalWebsocketClient extends WebSocketClient {

    private static final String SERVER_ADDRESS = "ws://%s:%d/%s";
    private static final int RESPONSE_WAITING_TIME = 5000;

    private final ReadWriteLock listenersLock = new ReentrantReadWriteLock(true);
    private final ReadWriteLock blockingLock = new ReentrantReadWriteLock(true);

    private final Logger logger;
    private final Gson gson;
    private final Map<Integer, List<Consumer<JsonObject>>> listeners = new HashMap<>();
    private final Map<Integer, List<CompletableFuture<JsonObject>>> responseFutures = new HashMap<>();

    public ArrivalWebsocketClient(WebSocketAddress serverAddress, Logger logger, Gson gson) {
        super(getServerURI(serverAddress));

        this.logger = logger;
        this.gson = gson;
    }

    public void addListener(int messageType, Consumer<JsonObject> onMessage) {
        listenersLock.writeLock().lock();
        try {
            if (!listeners.containsKey(messageType)) {
                listeners.put(messageType, new ArrayList<>());
            }

            listeners.get(messageType).add(onMessage);
        } finally {
            listenersLock.writeLock().unlock();
        }
    }

    public void send(Message message) {
        this.send(gson.toJson(message));
    }


    public Optional<JsonObject> sendBlocking(Message message, int responseType) {
        var future = new CompletableFuture<JsonObject>();

        blockingLock.writeLock().lock();
        try {
            if (!responseFutures.containsKey(responseType)) {
                responseFutures.put(responseType, new ArrayList<>());
            }

            responseFutures.get(responseType).add(future);
        } finally {
            blockingLock.writeLock().unlock();
        }

        this.send(message);

        try {
            return Optional.of(future.get(RESPONSE_WAITING_TIME, TimeUnit.MILLISECONDS));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return Optional.empty();
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        logger.info("Opened WebSocket connection to the server");
    }

    @Override
    public void onMessage(String rawMessage) {
        var message = gson.fromJson(rawMessage, RawMessage.class);
        boolean consumed = false;

        listenersLock.readLock().lock();
        try {
            if (listeners.containsKey(message.type())) {
                listeners.get(message.type()).forEach(c -> c.accept(message.body()));
                consumed = true;
            }
        } finally {
            listenersLock.readLock().unlock();
        }

        blockingLock.writeLock().lock();
        try {
            if (responseFutures.containsKey(message.type())) {
                responseFutures.get(message.type()).forEach(f -> f.complete(message.body()));
                responseFutures.remove(message.type());
                consumed = true;
            }
        } finally {
            blockingLock.writeLock().unlock();
        }

        if (!consumed) {
            logger.warn("There is no registered listener for client message type {}", message.type());
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

    private static URI getServerURI(WebSocketAddress serverAddress) {
        return URI.create(
                String.format(
                        SERVER_ADDRESS,
                        serverAddress.host(),
                        serverAddress.port(),
                        serverAddress.path()
                )
        );
    }
}
