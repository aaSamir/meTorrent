package com.metorrent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single protocol message: a {@link MessageType} plus a bag of typed
 * fields. Field names and meanings per type are documented in
 * {@code docs/PROTOCOL.md}. Instances are immutable once built.
 *
 * <p>A generic field map (rather than one Java class per message type) keeps
 * the protocol easy to extend and keeps {@link ProtocolSerializer} /
 * {@link ProtocolParser} trivial, at the cost of losing compile-time field
 * checking - callers use the typed {@code getX} accessors to fail fast on
 * malformed or missing fields instead.
 */
public final class Message {

    private final MessageType type;
    private final long timestamp;
    private final Map<String, Object> fields;

    @JsonCreator
    private Message(@JsonProperty("type") MessageType type,
                     @JsonProperty("timestamp") long timestamp,
                     @JsonProperty("fields") Map<String, Object> fields) {
        this.type = Objects.requireNonNull(type, "type");
        this.timestamp = timestamp;
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static Builder builder(MessageType type) {
        return new Builder(type);
    }

    public MessageType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public boolean hasField(String key) {
        return fields.containsKey(key);
    }

    public String getString(String key) {
        Object value = requireField(key);
        return value.toString();
    }

    public String getStringOrDefault(String key, String defaultValue) {
        return fields.containsKey(key) ? getString(key) : defaultValue;
    }

    public int getInt(String key) {
        return requireNumber(key).intValue();
    }

    public long getLong(String key) {
        return requireNumber(key).longValue();
    }

    public boolean getBoolean(String key) {
        Object value = requireField(key);
        if (value instanceof Boolean b) {
            return b;
        }
        throw new ProtocolException("Field '" + key + "' is not a boolean in message type " + type);
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object value = requireField(key);
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new ProtocolException("Field '" + key + "' is not a list in message type " + type);
    }

    private Object requireField(String key) {
        Object value = fields.get(key);
        if (value == null) {
            throw new ProtocolException("Missing required field '" + key + "' in message type " + type);
        }
        return value;
    }

    private Number requireNumber(String key) {
        Object value = requireField(key);
        if (value instanceof Number n) {
            return n;
        }
        throw new ProtocolException("Field '" + key + "' is not a number in message type " + type);
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", timestamp=" + timestamp + ", fields=" + fields + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message other)) return false;
        return timestamp == other.timestamp && type == other.type && fields.equals(other.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, timestamp, fields);
    }

    /** Builder for {@link Message}, per the protocol's builder-pattern requirement. */
    public static final class Builder {
        private final MessageType type;
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private long timestamp = System.currentTimeMillis();

        private Builder(MessageType type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder field(String key, Object value) {
            fields.put(key, value);
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Message build() {
            return new Message(type, timestamp, fields);
        }
    }
}
