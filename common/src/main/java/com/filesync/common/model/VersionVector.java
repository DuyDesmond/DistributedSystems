package com.filesync.common.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents a version vector for conflict detection and resolution
 */
public class VersionVector {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    @JsonProperty("vectors")
    private Map<String, Integer> vectors;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    public VersionVector() {
        this.vectors = new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }
    
    public VersionVector(Map<String, Integer> vectors) {
        this.vectors = new HashMap<>(vectors);
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Increment the version for a specific client
     */
    public void increment(@NotBlank String clientId) {
        vectors.put(clientId, vectors.getOrDefault(clientId, 0) + 1);
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Get version for a specific client
     */
    public int getVersion(String clientId) {
        return vectors.getOrDefault(clientId, 0);
    }
    
    /**
     * Check if this vector is concurrent with another (conflict detection)
     */
    public boolean isConcurrentWith(VersionVector other) {
        if (other == null) return false;
        
        boolean thisGreater = false;
        boolean otherGreater = false;
        
        // Check all clients in both vectors
        Map<String, Integer> allClients = new HashMap<>(this.vectors);
        allClients.putAll(other.vectors);
        
        for (String clientId : allClients.keySet()) {
            int thisVersion = this.getVersion(clientId);
            int otherVersion = other.getVersion(clientId);
            
            if (thisVersion > otherVersion) {
                thisGreater = true;
            } else if (otherVersion > thisVersion) {
                otherGreater = true;
            }
        }
        
        // Concurrent if both vectors have some greater versions
        return thisGreater && otherGreater;
    }
    
    /**
     * Check if this vector dominates another (this >= other)
     */
    public boolean dominates(VersionVector other) {
        if (other == null) return true;
        
        Map<String, Integer> allClients = new HashMap<>(this.vectors);
        allClients.putAll(other.vectors);
        
        for (String clientId : allClients.keySet()) {
            if (this.getVersion(clientId) < other.getVersion(clientId)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Merge with another version vector (taking max of each client)
     */
    public VersionVector merge(VersionVector other) {
        if (other == null) return new VersionVector(this.vectors);
        
        Map<String, Integer> merged = new HashMap<>(this.vectors);
        for (Map.Entry<String, Integer> entry : other.vectors.entrySet()) {
            merged.put(entry.getKey(), 
                Math.max(merged.getOrDefault(entry.getKey(), 0), entry.getValue()));
        }
        
        return new VersionVector(merged);
    }
    
    /**
     * Convert this VersionVector to JSON string
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize VersionVector to JSON", e);
        }
    }
    
    /**
     * Create VersionVector from JSON string
     */
    public static VersionVector fromJson(String json) {
        try {
            return objectMapper.readValue(json, VersionVector.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize VersionVector from JSON", e);
        }
    }

    // Getters and setters
    public Map<String, Integer> getVectors() {
        return new HashMap<>(vectors);
    }
    
    public void setVectors(Map<String, Integer> vectors) {
        this.vectors = new HashMap<>(vectors);
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionVector that = (VersionVector) o;
        return Objects.equals(vectors, that.vectors);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(vectors);
    }
    
    @Override
    public String toString() {
        return "VersionVector{" +
                "vectors=" + vectors +
                ", timestamp=" + timestamp +
                '}';
    }
}
