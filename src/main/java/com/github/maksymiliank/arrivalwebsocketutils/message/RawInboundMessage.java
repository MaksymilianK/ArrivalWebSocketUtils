package com.github.maksymiliank.arrivalwebsocketutils.message;

import com.google.gson.JsonObject;

public final class RawInboundMessage implements Message {

    private final int type;
    private final JsonObject body;

    public RawInboundMessage(int type, JsonObject body) {
        this.type = type;
        this.body = body;
    }

    @Override
    public int getType() {
        return type;
    }

    public JsonObject getBody() {
        return body;
    }
}
