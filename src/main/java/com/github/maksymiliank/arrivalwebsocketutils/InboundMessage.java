package com.github.maksymiliank.arrivalwebsocketutils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class InboundMessage {

    private final int type;
    private final JsonObject body;

    public InboundMessage(int type, JsonObject body) {
        this.type = type;
        this.body = body;
    }

    public int getType() {
        return type;
    }

    public JsonElement getBody() {
        return body;
    }
}
