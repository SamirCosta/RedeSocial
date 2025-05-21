package com.redesocial.model;

import org.json.JSONObject;

import java.io.Serializable;

public class ReplicationEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String TYPE_USER_CREATED = "USER_CREATED";
    public static final String TYPE_USER_UPDATED = "USER_UPDATED";
    public static final String TYPE_FOLLOW_ADDED = "FOLLOW_ADDED";
    public static final String TYPE_FOLLOW_REMOVED = "FOLLOW_REMOVED";
    public static final String TYPE_POST_CREATED = "POST_CREATED";
    public static final String TYPE_POST_UPDATED = "POST_UPDATED";
    public static final String TYPE_POST_DELETED = "POST_DELETED";
    public static final String TYPE_MESSAGE_SENT = "MESSAGE_SENT";

    private final String type;
    private final String entityId;
    private final long timestamp;
    private final JSONObject data;

    public ReplicationEvent(String type, String entityId, long timestamp, JSONObject data) {
        this.type = type;
        this.entityId = entityId;
        this.timestamp = timestamp;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public String getEntityId() {
        return entityId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public JSONObject getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ReplicationEvent{" +
                "type='" + type + '\'' +
                ", entityId='" + entityId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}