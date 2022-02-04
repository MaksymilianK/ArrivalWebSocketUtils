package com.github.maksymiliank.arrivalwebsocketutils.message;

import com.google.gson.JsonObject;

public record RawMessage (int type, JsonObject body) {}
