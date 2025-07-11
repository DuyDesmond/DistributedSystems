package com.filesync.server.repository;

import com.filesync.server.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for UserEntity
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    
    Optional<UserEntity> findByUsername(String username);
    
    Optional<UserEntity> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM UserEntity u WHERE u.username = :username OR u.email = :email")
    Optional<UserEntity> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);
}
