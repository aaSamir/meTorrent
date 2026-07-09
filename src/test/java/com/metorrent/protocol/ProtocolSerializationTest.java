package com.metorrent.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolSerializationTest {

    private final ProtocolSerializer serializer = new ProtocolSerializer();
    private final ProtocolParser parser = new ProtocolParser();

    @Test
    void roundTripsSimpleHelloMessage() {
        Message original = Message.builder(MessageType.HELLO)
                .field("peerName", "Alice")
                .field("protocolVersion", 1)
                .field("listenPort", 7777)
                .build();

        Message decoded = parser.parse(serializer.serialize(original));

        assertEquals(MessageType.HELLO, decoded.getType());
        assertEquals("Alice", decoded.getString("peerName"));
        assertEquals(1, decoded.getInt("protocolVersion"));
        assertEquals(7777, decoded.getInt("listenPort"));
        assertEquals(original.getTimestamp(), decoded.getTimestamp());
    }

    @Test
    void roundTripsFileChunkHeaderWithLongFields() {
        Message original = Message.builder(MessageType.FILE_CHUNK)
                .field("transferId", "abc-123")
                .field("sequenceNumber", 42)
                .field("offset", 5_000_000_000L) // exceeds int range
                .field("length", 262144)
                .field("checksum", "deadbeef")
                .build();

        Message decoded = parser.parse(serializer.serializeToString(original));

        assertEquals(5_000_000_000L, decoded.getLong("offset"));
        assertEquals(42, decoded.getInt("sequenceNumber"));
        assertEquals("deadbeef", decoded.getString("checksum"));
    }

    @Test
    void roundTripsNestedListField() {
        Message original = Message.builder(MessageType.FILE_LIST)
                .field("files", List.of(
                        Map.of("name", "a.txt", "size", 100),
                        Map.of("name", "b.txt", "size", 200)))
                .build();

        Message decoded = parser.parse(serializer.serialize(original));

        List<Object> files = decoded.getList("files");
        assertEquals(2, files.size());
    }

    @Test
    void getBooleanRoundTrips() {
        Message original = Message.builder(MessageType.ACK)
                .field("sequenceNumber", 3)
                .field("success", false)
                .build();

        Message decoded = parser.parse(serializer.serialize(original));
        assertEquals(false, decoded.getBoolean("success"));
    }

    @Test
    void missingRequiredFieldThrowsProtocolException() {
        Message message = Message.builder(MessageType.PING).build();
        assertThrows(ProtocolException.class, () -> message.getString("nonexistent"));
    }

    @Test
    void wrongFieldTypeThrowsProtocolException() {
        Message message = Message.builder(MessageType.PING).field("count", "not-a-number").build();
        assertThrows(ProtocolException.class, () -> message.getInt("count"));
    }

    @Test
    void malformedJsonThrowsProtocolException() {
        assertThrows(ProtocolException.class, () -> parser.parse("{ not valid json"));
    }

    @Test
    void getStringOrDefaultReturnsFallbackWhenAbsent() {
        Message message = Message.builder(MessageType.PONG).build();
        assertEquals("fallback", message.getStringOrDefault("missing", "fallback"));
    }

    @Test
    void serializedMessageIsHumanReadableJson() {
        Message message = Message.builder(MessageType.DISCONNECT).field("reason", "user quit").build();
        String json = serializer.serializeToString(message);
        assertTrue(json.contains("\"DISCONNECT\""));
        assertTrue(json.contains("user quit"));
    }
}
