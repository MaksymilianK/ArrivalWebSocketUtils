package com.github.maksymiliank.arrivalwebsocketutils.message;

public abstract class OutboundMessage implements Message {

    private final int type;

    public OutboundMessage(int type) {
        this.type = type;
    }

    @Override
    public int getType() {
        return type;
    }
}
