package com.example.meetus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {
    private String from;
    private String to;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String username;
} 