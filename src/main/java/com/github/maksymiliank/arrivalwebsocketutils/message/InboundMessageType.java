package com.github.maksymiliank.arrivalwebsocketutils.message;

public enum InboundMessageType {

    CLIENT_REGISTRATION (0);

    private final int type;

    InboundMessageType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }
}
