package com.github.maksymiliank.arrivalwebsocketutils.message;

public final class ClientRegistrationInboundMessage implements Message {

    private String clientName;

    public ClientRegistrationInboundMessage() {}

    public ClientRegistrationInboundMessage(String clientName) {
        this.clientName = clientName;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    @Override
    public int getType() {
        return InboundMessageType.CLIENT_REGISTRATION.getType();
    }
}
