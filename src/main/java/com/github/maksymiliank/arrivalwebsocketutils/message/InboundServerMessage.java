package com.github.maksymiliank.arrivalwebsocketutils.message;

public abstract class InboundServerMessage implements Message {

    private int server;

    public InboundServerMessage() {}

    public int getServer() {
        return server;
    }

    public void setServer(int server) {
        this.server = server;
    }
}
