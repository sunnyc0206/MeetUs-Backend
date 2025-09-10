package com.example.meetus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomRequest {
    private String roomId;
    private String username;
    private String password;
} 