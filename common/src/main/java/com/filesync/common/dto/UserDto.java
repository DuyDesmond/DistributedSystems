package com.filesync.common.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for user authentication
 */
public class UserDto {
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("username")
    @NotBlank
    private String username;
    
    @JsonProperty("email")
    @NotBlank
    private String email;
    
    @JsonProperty("password")
    private String password; // Only used for registration/login
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("last_login")
    private LocalDateTime lastLogin;
    
    @JsonProperty("storage_quota")
    private Long storageQuota;
    
    @JsonProperty("used_storage")
    private Long usedStorage;
    
    @JsonProperty("account_status")
    private String accountStatus;
    
    // Constructors
    public UserDto() {}
    
    public UserDto(String username, String email) {
        this.username = username;
        this.email = email;
    }
    
    // Getters and setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public Long getStorageQuota() {
        return storageQuota;
    }
    
    public void setStorageQuota(Long storageQuota) {
        this.storageQuota = storageQuota;
    }
    
    public Long getUsedStorage() {
        return usedStorage;
    }
    
    public void setUsedStorage(Long usedStorage) {
        this.usedStorage = usedStorage;
    }
    
    public String getAccountStatus() {
        return accountStatus;
    }
    
    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }
}
