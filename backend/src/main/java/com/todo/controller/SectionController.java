package com.todo.controller;

import com.todo.dto.request.SectionRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.SectionResponse;
import com.todo.entity.User;
import com.todo.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/sections")
@RequiredArgsConstructor
@Validated
public class SectionController {

    private final SectionService sectionService;

    @GetMapping
    public ApiResponse<List<SectionResponse>> list(@AuthenticationPrincipal User user,
                                                   @PathVariable Long projectId) {
        return ApiResponse.ok(sectionService.list(projectId, user.getId()));
    }

    @PostMapping
    public ApiResponse<SectionResponse> create(@AuthenticationPrincipal User user,
                                               @PathVariable Long projectId,
                                               @Valid @RequestBody SectionRequest req) {
        return ApiResponse.ok(sectionService.create(projectId, user.getId(), req.getName()), "分区创建成功");
    }

    @PutMapping("/{sectionId}")
    public ApiResponse<SectionResponse> update(@AuthenticationPrincipal User user,
                                               @PathVariable Long projectId,
                                               @PathVariable Long sectionId,
                                               @Valid @RequestBody SectionRequest req) {
        return ApiResponse.ok(sectionService.update(sectionId, user.getId(), req.getName()));
    }

    @DeleteMapping("/{sectionId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user,
                                    @PathVariable Long projectId,
                                    @PathVariable Long sectionId) {
        sectionService.delete(sectionId, user.getId());
        return ApiResponse.ok(null, "分区已删除");
    }

    @PutMapping("/reorder")
    public ApiResponse<Void> reorder(@AuthenticationPrincipal User user,
                                     @PathVariable Long projectId,
                                     @RequestBody @NotEmpty(message = "排序列表不能为空") List<Long> orderedIds) {
        sectionService.reorder(projectId, user.getId(), orderedIds);
        return ApiResponse.ok(null, "排序已更新");
    }
}