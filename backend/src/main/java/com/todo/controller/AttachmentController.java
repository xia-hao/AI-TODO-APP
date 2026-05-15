package com.todo.controller;

import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.AttachmentResponse;
import com.todo.entity.Attachment;
import com.todo.entity.User;
import com.todo.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/todos/{todoId}/attachments")
@RequiredArgsConstructor
@Validated
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public ApiResponse<List<AttachmentResponse>> list(@AuthenticationPrincipal User user,
                                                      @PathVariable Long todoId) {
        return ApiResponse.ok(attachmentService.list(todoId, user.getId()));
    }

    @PostMapping
    public ApiResponse<AttachmentResponse> upload(@AuthenticationPrincipal User user,
                                                  @PathVariable Long todoId,
                                                  @RequestParam("file") @NotNull(message = "上传文件不能为空") MultipartFile file) {
        return ApiResponse.ok(attachmentService.upload(todoId, user.getId(), file), "上传成功");
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user,
                                    @PathVariable Long todoId,
                                    @PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId, user.getId());
        return ApiResponse.ok(null, "附件已删除");
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal User user,
                                             @PathVariable Long todoId,
                                             @PathVariable Long attachmentId) {
        Attachment attachment = attachmentService.getById(attachmentId);
        // 简单校验：附件归属的 todoId 是否匹配（防止越权）
        if (!attachment.getTodoId().equals(todoId)) {
            return ResponseEntity.notFound().build();
        }

        String uploadDir = attachmentService.getUploadDir(); // 需在 service 中暴露
        Path filePath = Paths.get(uploadDir, attachment.getFilePath());
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) return ResponseEntity.notFound().build();

        try {
            String encodedName = URLEncoder.encode(attachment.getFileName(), "UTF-8")
                    .replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedName)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}