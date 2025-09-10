package com.example.meetus.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class Room {
    private String roomId;
    private List<User> users = new ArrayList<>();
    private String password;
    private Instant createdAt;
    private String createdBy;
    
    public Room(String roomId) {
        this.roomId = roomId;
        this.createdAt = Instant.now();
        this.users = new ArrayList<>();
    }
    
    public void addUser(User user) {
        users.add(user);
    }
    
    public void removeUser(String userId) {
        users.removeIf(user -> user.getId().equals(userId));
    }
    
    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }
    
    public boolean validatePassword(String inputPassword) {
        if (!hasPassword()) {
            return true;
        }
        return password.equals(inputPassword);
    }
} 