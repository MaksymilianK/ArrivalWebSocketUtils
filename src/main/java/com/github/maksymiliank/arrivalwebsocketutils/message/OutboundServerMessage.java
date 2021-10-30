package com.github.maksymiliank.arrivalwebsocketutils.message;

public abstract class OutboundServerMessage extends OutboundMessage {

    private final int server;

    public OutboundServerMessage(int type, int server) {
        super(type);
        this.server = server;
    }

    public int getServer() {
        return server;
    }
}
