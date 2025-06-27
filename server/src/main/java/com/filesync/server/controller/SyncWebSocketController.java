package com.filesync.server.controller;

import com.filesync.common.dto.SyncEventDto;
import com.filesync.server.entity.SyncEventEntity;
import com.filesync.server.entity.UserEntity;
import com.filesync.server.repository.SyncEventRepository;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebSocket controller for real-time file synchronization
 */
@Controller
public class SyncWebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncWebSocketController.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private SyncEventRepository syncEventRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Handle heartbeat messages from clients
     */
    @MessageMapping("/heartbeat")
    @SendToUser("/queue/heartbeat")
    public SyncEventDto handleHeartbeat(@Payload SyncEventDto heartbeat, 
                                       @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        logger.debug("Received heartbeat from user: {}", userPrincipal.getUsername());
        
        // Update last seen timestamp for the client
        // This could be stored in Redis for better performance
        
        // Send acknowledgment back to client
        SyncEventDto response = new SyncEventDto();
        response.setEventType("HEARTBEAT_ACK");
        response.setTimestamp(LocalDateTime.now());
        response.setClientId(heartbeat.getClientId());
        
        return response;
    }
    
    /**
     * Handle sync status updates from clients
     */
    @MessageMapping("/sync/status")
    public void handleSyncStatus(@Payload SyncEventDto syncStatus,
                                @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        logger.debug("Received sync status from {}: {}", userPrincipal.getUsername(), syncStatus.getEventType());
        
        try {
            UserEntity user = userRepository.findByUsername(userPrincipal.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Create sync event record
            SyncEventEntity syncEvent = new SyncEventEntity();
            syncEvent.setUser(user);
            syncEvent.setEventType(SyncEventEntity.EventType.valueOf(syncStatus.getEventType()));
            syncEvent.setClientId(syncStatus.getClientId());
            syncEvent.setTimestamp(LocalDateTime.now());
            syncEvent.setFilePath(syncStatus.getFilePath());
            syncEvent.setSyncStatus(SyncEventEntity.SyncStatus.COMPLETED);
            
            syncEventRepository.save(syncEvent);
            
            // Broadcast sync event to other clients of the same user
            broadcastSyncEvent(userPrincipal.getUsername(), syncStatus, syncStatus.getClientId());
            
        } catch (Exception e) {
            logger.error("Error handling sync status update", e);
        }
    }
    
    /**
     * Handle file change notifications from clients
     */
    @MessageMapping("/sync/file-change")
    public void handleFileChange(@Payload SyncEventDto fileChange,
                                @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        logger.debug("Received file change from {}: {} - {}", 
                    userPrincipal.getUsername(), fileChange.getEventType(), fileChange.getFilePath());
        
        try {
            UserEntity user = userRepository.findByUsername(userPrincipal.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Create sync event record
            SyncEventEntity syncEvent = new SyncEventEntity();
            syncEvent.setUser(user);
            syncEvent.setEventType(SyncEventEntity.EventType.valueOf(fileChange.getEventType()));
            syncEvent.setClientId(fileChange.getClientId());
            syncEvent.setTimestamp(LocalDateTime.now());
            syncEvent.setFilePath(fileChange.getFilePath());
            syncEvent.setSyncStatus(SyncEventEntity.SyncStatus.PENDING);
            
            syncEventRepository.save(syncEvent);
            
            // Broadcast file change to other clients of the same user
            broadcastSyncEvent(userPrincipal.getUsername(), fileChange, fileChange.getClientId());
            
        } catch (Exception e) {
            logger.error("Error handling file change notification", e);
        }
    }
    
    /**
     * Handle client requesting sync updates
     */
    @MessageMapping("/sync/get-updates")
    @SendToUser("/queue/sync-updates")
    public List<SyncEventDto> handleGetUpdates(@Payload SyncEventDto request,
                                              @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        logger.debug("Client {} requesting sync updates since: {}", 
                    userPrincipal.getUsername(), request.getTimestamp());
        
        try {
            UserEntity user = userRepository.findByUsername(userPrincipal.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            LocalDateTime since = request.getTimestamp() != null ? 
                    request.getTimestamp() : LocalDateTime.now().minusHours(24);
            
            List<SyncEventEntity> events = syncEventRepository.findByUserSinceLastSync(user, since);
            
            return events.stream()
                    .map(this::convertToDto)
                    .toList();
                    
        } catch (Exception e) {
            logger.error("Error getting sync updates", e);
            return List.of();
        }
    }
    
    /**
     * Broadcast sync event to all other clients of a user
     */
    private void broadcastSyncEvent(String username, SyncEventDto syncEvent, String excludeClientId) {
        // Send to all sessions of the user except the sender
        messagingTemplate.convertAndSendToUser(
            username, 
            "/queue/file-changes", 
            syncEvent
        );
    }
    
    /**
     * Convert SyncEventEntity to SyncEventDto
     */
    private SyncEventDto convertToDto(SyncEventEntity entity) {
        SyncEventDto dto = new SyncEventDto();
        dto.setEventType(entity.getEventType().name());
        dto.setFilePath(entity.getFilePath());
        dto.setTimestamp(entity.getTimestamp());
        dto.setClientId(entity.getClientId());
        dto.setFileSize(entity.getFileSize());
        dto.setChecksum(entity.getChecksum());
        return dto;
    }
    
    /**
     * Send sync notification to specific user
     */
    public void notifyUserFileChange(String username, SyncEventDto syncEvent) {
        messagingTemplate.convertAndSendToUser(username, "/queue/file-changes", syncEvent);
    }
    
    /**
     * Send sync conflict notification to user
     */
    public void notifyUserConflict(String username, SyncEventDto conflictEvent) {
        messagingTemplate.convertAndSendToUser(username, "/queue/conflicts", conflictEvent);
    }
}
