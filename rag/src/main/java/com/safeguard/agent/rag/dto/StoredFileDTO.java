package com.safeguard.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StoredFileDTO {

    private String url;

    private String detectedType;

    private String mimeType;

    private Long size;

    private String originalFilename;
}
