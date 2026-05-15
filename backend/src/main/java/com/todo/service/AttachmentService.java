package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.response.AttachmentResponse;
import com.todo.entity.Attachment;
import com.todo.exception.AppException;
import com.todo.mapper.AttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final TodoService todoService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-size:10485760}")
    private long maxFileSize;

    @Value("${app.upload.allowed-types:image/jpeg,image/png,image/gif,image/webp,application/pdf,text/plain,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}")
    private String allowedTypes;

    private Set<String> getAllowedTypesSet() {
        return java.util.Arrays.stream(allowedTypes.split(",")).collect(Collectors.toSet());
    }

    public List<AttachmentResponse> list(Long todoId, Long userId) {
        todoService.getAndAssertAccess(todoId, userId, false);
        List<Attachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getTodoId, todoId)
                        .orderByDesc(Attachment::getCreateTime));
        return attachments.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public AttachmentResponse upload(Long todoId, Long userId, MultipartFile file) {
        todoService.getAndAssertAccess(todoId, userId, false);
        if (file.isEmpty()) throw AppException.badRequest("文件为空");
        if (file.getSize() > maxFileSize) throw AppException.badRequest("文件大小不能超过 " + (maxFileSize / 1048576) + "MB");
        if (file.getContentType() == null || !getAllowedTypesSet().contains(file.getContentType()))
            throw AppException.badRequest("不支持的文件类型: " + file.getContentType());

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")) : "";
        String storedName = UUID.randomUUID().toString() + ext;
        String datePath = LocalDate.now().toString();
        Path targetDir = Paths.get(getUploadDir(), datePath);
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(storedName);
            file.transferTo(targetFile.toFile());

            Attachment attachment = new Attachment();
            attachment.setTodoId(todoId);
            attachment.setUserId(userId);
            attachment.setFileName(originalName);
            attachment.setFilePath(datePath + "/" + storedName);
            attachment.setFileSize(file.getSize());
            attachment.setMimeType(file.getContentType());
            attachmentMapper.insert(attachment);
            return toResponse(attachment);
        } catch (IOException e) {
            throw new AppException("文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void delete(Long attachmentId, Long userId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) throw AppException.notFound("附件");
        // 仅允许附件上传者或管理员删除
        if (!attachment.getUserId().equals(userId)) {
            // 可以加入管理员判断，为简单起见仅限上传者
            throw AppException.forbidden();
        }
        // 删除物理文件
        try {
            Path filePath = Paths.get(getUploadDir(), attachment.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {}
        attachmentMapper.deleteById(attachmentId);
    }

    public Attachment getById(Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) throw AppException.notFound("附件");
        return attachment;
    }

    public String getUploadDir(){
        return uploadDir;
    }

    private AttachmentResponse toResponse(Attachment a) {
        AttachmentResponse r = new AttachmentResponse();
        r.setId(a.getId());
        r.setTodoId(a.getTodoId());
        r.setFileName(a.getFileName());
        r.setFileSize(a.getFileSize());
        r.setMimeType(a.getMimeType());
        r.setCreateTime(a.getCreateTime());
        return r;
    }
}