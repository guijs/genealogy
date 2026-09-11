package com.genealogy.web;

import com.genealogy.service.MediaService;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.UploadUrlRequest;
import com.genealogy.web.dto.UploadUrlResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families/{familyId}/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> getUploadUrl(HttpServletRequest request, @RequestBody UploadUrlRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        MediaService.UploadUrlResult result = mediaService.getUploadUrl(
                familyId,
                body.getMimeType(),
                body.getFileSize() != null ? body.getFileSize() : 0L,
                body.getStorageKey()
        );

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(result.getError()));
        }

        return ResponseEntity.ok(new UploadUrlResponse(result.getUploadUrl(), result.getStorageKey()));
    }
}
