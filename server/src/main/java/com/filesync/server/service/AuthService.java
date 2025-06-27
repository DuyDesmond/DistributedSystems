package com.filesync.server.service;

import com.filesync.common.dto.AuthDto;
import com.filesync.common.dto.UserDto;
import com.filesync.server.entity.UserEntity;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.security.JwtUtils;
import com.filesync.server.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Authentication Service
 */
@Service
public class AuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    public UserDto registerUser(AuthDto signUpRequest) {
        // Check if username already exists
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new RuntimeException("Error: Username is already taken!");
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already in use!");
        }
        
        // Create new user
        UserEntity user = new UserEntity(
            UUID.randomUUID().toString(),
            signUpRequest.getUsername(),
            signUpRequest.getEmail(),
            passwordEncoder.encode(signUpRequest.getPassword())
        );
        
        userRepository.save(user);
        
        // Convert to DTO
        UserDto userDto = new UserDto();
        userDto.setUserId(user.getUserId());
        userDto.setUsername(user.getUsername());
        userDto.setEmail(user.getEmail());
        userDto.setCreatedAt(user.getCreatedAt());
        userDto.setStorageQuota(user.getStorageQuota());
        userDto.setUsedStorage(user.getUsedStorage());
        userDto.setAccountStatus(user.getAccountStatus());
        
        return userDto;
    }
    
    public AuthDto authenticateUser(AuthDto loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String jwt = jwtUtils.generateJwtToken(authentication);
        String refreshToken = jwtUtils.generateRefreshToken(
            ((UserPrincipal) authentication.getPrincipal()).getUsername()
        );
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        return AuthDto.tokenResponse(jwt, refreshToken, 86400L, userPrincipal.getUserId());
    }
    
    public AuthDto refreshToken(String refreshToken) {
        if (refreshToken != null && jwtUtils.validateJwtToken(refreshToken)) {
            String username = jwtUtils.getUserNameFromJwtToken(refreshToken);
            String newToken = jwtUtils.generateJwtToken(username);
            String newRefreshToken = jwtUtils.generateRefreshToken(username);
            
            UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            return AuthDto.tokenResponse(newToken, newRefreshToken, 86400L, user.getUserId());
        } else {
            throw new RuntimeException("Invalid refresh token");
        }
    }
}
