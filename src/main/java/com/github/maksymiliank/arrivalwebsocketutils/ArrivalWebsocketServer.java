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
import java.util.*;
import java.util.function.BiConsumer;

public class ArrivalWebsocketServer extends WebSocketServer {

    public static final int CODE_UNAUTHORIZED = 4000;
    public static final int CODE_ALREADY_CONNECTED = 4001;

    private final Logger logger;
    private final Gson gson;
    private final Map<InetSocketAddress, Integer> allowedClientAddresses = new HashMap<>();
    private final Map<Integer, WebSocket> connectedClients = new HashMap<>();
    private final Map<Integer, List<BiConsumer<Integer, InboundMessage>>> listeners = new HashMap<>();

    public ArrivalWebsocketServer(Logger logger, int port, Map<InetSocketAddress, Integer> allowedClientAddresses) {
        super(new InetSocketAddress("localhost", port));

        this.logger = logger;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Integer.class, new InboundMessageDeserializer())
                .create();
        this.allowedClientAddresses.putAll(allowedClientAddresses);
    }

    public void addListener(int messageType, BiConsumer<Integer, InboundMessage> onMessage) {
        if (!listeners.containsKey(messageType)) {
            listeners.put(messageType, new ArrayList<>());
        }

        listeners.get(messageType).add(onMessage);
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft,
                                                                       ClientHandshake request) throws InvalidDataException {
        ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);

        if (!allowedClientAddresses.containsKey(conn.getRemoteSocketAddress())) {
            throw new InvalidDataException(
                    CODE_UNAUTHORIZED,
                    String.format("Connection from the address '%s' is not allowed", conn.getRemoteSocketAddress())
            );
        }

        int clientId = allowedClientAddresses.get(conn.getRemoteSocketAddress());
        if (connectedClients.containsKey(clientId)) {
            throw new InvalidDataException(
                    CODE_ALREADY_CONNECTED,
                    String.format("The client %d is not allowed", clientId)
            );
        }

        return builder;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        int clientId = getClientId(conn);
        connectedClients.put(clientId, conn);
        logger.info("Opened connection from client {}", clientId);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        int clientId = getClientId(conn);
        connectedClients.remove(clientId);
        logger.info("Closed connection from client {}", clientId);
    }

    @Override
    public void onMessage(WebSocket conn, String rawMessage) {
        int clientId = getClientId(conn);
        var message = gson.fromJson(rawMessage, InboundMessage.class);

        if (listeners.containsKey(message.getType())) {
            listeners.get(message.getType()).forEach(c -> c.accept(clientId, message));
        } else {
            logger.warn("There is no registered listener for message type {}", message.getType());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error(ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server is running");
    }

    private int getClientId(WebSocket conn) {
        return allowedClientAddresses.get(conn.getRemoteSocketAddress());
    }
}
