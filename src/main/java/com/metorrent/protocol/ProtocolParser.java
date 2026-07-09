package com.metorrent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Decodes UTF-8 JSON received over the wire back into {@link Message} instances. */
public class ProtocolParser {

    private final ObjectMapper mapper;

    public ProtocolParser() {
        this.mapper = new ObjectMapper();
    }

    public Message parse(byte[] json) {
        try {
            return mapper.readValue(json, Message.class);
        } catch (IOException e) {
            throw new ProtocolException("Failed to parse message: " + e.getMessage(), e);
        }
    }

    public Message parse(String json) {
        return parse(json.getBytes(StandardCharsets.UTF_8));
    }
}
