package com.github.maksymiliank.arrivalwebsocketutils;

public abstract class OutboundMessage {

    private final int type;

    public OutboundMessage(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }
}
