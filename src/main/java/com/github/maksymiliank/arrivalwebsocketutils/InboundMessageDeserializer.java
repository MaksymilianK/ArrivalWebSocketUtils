package com.github.maksymiliank.arrivalwebsocketutils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

class InboundMessageDeserializer implements JsonDeserializer<InboundMessage> {

    @Override
    public InboundMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        var body = json.getAsJsonObject();

        var typeJson = body.get("type");
        if (typeJson == null) {
            throw new JsonParseException("Message type is not present");
        }

        int type;
        try {
            type = typeJson.getAsInt();
        } catch (ClassCastException | IllegalStateException e) {
            throw new JsonParseException("Message type is not an integer");
        }
        body.remove("type");

        return new InboundMessage(type, body);
    }
}