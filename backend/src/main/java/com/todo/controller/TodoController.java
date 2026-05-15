package com.todo.controller;

import com.todo.dto.request.*;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.TodoResponse;
import com.todo.dto.request.ImportItem;
import com.todo.dto.response.TodoSearchResult;
import com.todo.entity.User;
import com.todo.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
@Slf4j
@Validated
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public ApiResponse<List<TodoResponse>> list(
            @AuthenticationPrincipal User user,
            TodoListQuery query) {
        query.setUserId(user.getId());
        return ApiResponse.ok(todoService.list(query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TodoResponse> create(@AuthenticationPrincipal User user,
                                             @Valid @RequestBody TodoRequest req) {
        return ApiResponse.ok(todoService.create(user.getId(), req), "创建成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoResponse> update(@AuthenticationPrincipal User user,
                                             @PathVariable Long id,
                                             @Valid @RequestBody TodoRequest req) {
        return ApiResponse.ok(todoService.update(id, user.getId(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<TodoResponse> getById(@AuthenticationPrincipal User user,
                                              @PathVariable Long id) {
        return ApiResponse.ok(todoService.getById(id, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        todoService.delete(id, user.getId());
        return ApiResponse.ok(null, "已移入回收站");
    }

    @PatchMapping("/{id}/restore")
    public ApiResponse<TodoResponse> restore(@AuthenticationPrincipal User user,
                                              @PathVariable Long id) {
        todoService.restore(id, user.getId());
        return ApiResponse.ok(null, "已恢复");
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> permanentlyDelete(@AuthenticationPrincipal User user,
                                                @PathVariable Long id) {
        todoService.permanentlyDelete(id, user.getId());
        return ApiResponse.ok(null, "已彻底删除");
    }

    @GetMapping("/deleted")
    public ApiResponse<List<TodoResponse>> listDeleted(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(todoService.listDeleted(user.getId()));
    }

    @PatchMapping("/{id}/complete")
    public ApiResponse<TodoResponse> toggleComplete(@AuthenticationPrincipal User user,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(todoService.toggleComplete(id, user.getId()));
    }

    @PutMapping("/reorder")
    public ApiResponse<Void> reorder(@AuthenticationPrincipal User user,
                                      @Valid @RequestBody List<ReorderItem> items) {
        todoService.reorder(user.getId(), items);
        return ApiResponse.ok(null, "排序已更新");
    }

    @PostMapping("/import/{projectId}")
    public ApiResponse<List<TodoResponse>> importTodos(@AuthenticationPrincipal User user,
                                                        @PathVariable Long projectId,
                                                        @Valid @RequestBody List<ImportItem> items) {
        return ApiResponse.ok(todoService.batchImport(projectId, user.getId(), items));
    }

    @GetMapping("/search")
    public ApiResponse<List<TodoSearchResult>> search(@AuthenticationPrincipal User user,
                                                       @RequestParam @NotBlank(message = "搜索关键词不能为空") String q) {
        return ApiResponse.ok(todoService.search(user.getId(), q));
    }

    @GetMapping("/export")
    public ApiResponse<List<TodoResponse>> export(@AuthenticationPrincipal User user,
                                                   TodoListQuery query) {
        query.setUserId(user.getId());
        return ApiResponse.ok(todoService.export(query));
    }

    @GetMapping("/by-project")
    public ApiResponse<List<TodoResponse>> listByProject(
            @AuthenticationPrincipal User user,
            @Valid TodoProjectQuery query) {
        query.setUserId(user.getId());
        return ApiResponse.ok(todoService.listByProject(query));
    }

    @PatchMapping("/{id}/move-section")
    public ApiResponse<TodoResponse> moveSection(@AuthenticationPrincipal User user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody MoveSectionRequest req) {
        return ApiResponse.ok(todoService.moveSection(id, req.getSectionId(), user.getId()));
    }

}
