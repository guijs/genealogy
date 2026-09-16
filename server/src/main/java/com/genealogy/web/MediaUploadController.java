package com.genealogy.web;

import com.genealogy.store.LocalObjectStore;
import com.genealogy.store.MediaUploadToken;
import com.genealogy.store.PathTraversalException;
import com.genealogy.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/media/uploads")
@ConditionalOnProperty(name = "media.storage", havingValue = "local", matchIfMissing = true)
public class MediaUploadController {

    private final LocalObjectStore localObjectStore;

    public MediaUploadController(LocalObjectStore localObjectStore) {
        this.localObjectStore = localObjectStore;
    }

    @PutMapping("/{token}")
    public ResponseEntity<?> uploadFile(
            @PathVariable("token") String token,
            HttpServletRequest request) {

        MediaUploadToken uploadToken = localObjectStore.validateToken(token);
        if (uploadToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("invalid or expired upload token"));
        }

        String contentType = request.getContentType();
        if (contentType == null || !normalizeContentType(contentType).equals(uploadToken.getMimeType())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Content-Type mismatch: expected " + uploadToken.getMimeType()));
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Content-Length header required"));
        }

        if (contentLength > uploadToken.getMaxSize()) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ErrorResponse("file too large: maximum " + uploadToken.getMaxSize() + " bytes allowed"));
        }

        if (contentLength == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("empty file not allowed"));
        }

        try {
            localObjectStore.storeFile(uploadToken.getStorageKey(), request.getInputStream(), contentLength);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (PathTraversalException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid storage key"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("failed to store file"));
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolonIndex = contentType.indexOf(';');
        if (semicolonIndex > 0) {
            contentType = contentType.substring(0, semicolonIndex);
        }
        return contentType.toLowerCase().trim();
    }
}
