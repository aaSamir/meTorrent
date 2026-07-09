package com.metorrent.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** Encodes {@link Message} instances to UTF-8 JSON for transmission over the wire. */
public class ProtocolSerializer {

    private final ObjectMapper mapper;

    public ProtocolSerializer() {
        this.mapper = new ObjectMapper();
    }

    public byte[] serialize(Message message) {
        try {
            return mapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("Failed to serialize message of type " + message.getType(), e);
        }
    }

    public String serializeToString(Message message) {
        return new String(serialize(message), StandardCharsets.UTF_8);
    }
}
