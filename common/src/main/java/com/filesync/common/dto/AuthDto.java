package com.filesync.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for authentication requests and responses
 */
public class AuthDto {
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("password")
    private String password;
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("token_type")
    private String tokenType = "Bearer";
    
    @JsonProperty("expires_in")
    private Long expiresIn;
    
    @JsonProperty("user_id")
    private String userId;
    
    // Constructors
    public AuthDto() {}
    
    public AuthDto(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    // Static factory methods
    public static AuthDto loginRequest(String username, String password) {
        return new AuthDto(username, password);
    }
    
    public static AuthDto registerRequest(String username, String email, String password) {
        AuthDto dto = new AuthDto();
        dto.setUsername(username);
        dto.setEmail(email);
        dto.setPassword(password);
        return dto;
    }
    
    public static AuthDto tokenResponse(String accessToken, String refreshToken, Long expiresIn, String userId) {
        AuthDto dto = new AuthDto();
        dto.setAccessToken(accessToken);
        dto.setRefreshToken(refreshToken);
        dto.setExpiresIn(expiresIn);
        dto.setUserId(userId);
        return dto;
    }
    
    // Getters and setters
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
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    
    public Long getExpiresIn() {
        return expiresIn;
    }
    
    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
}
