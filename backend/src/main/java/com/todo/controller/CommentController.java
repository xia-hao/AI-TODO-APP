package com.todo.controller;

import com.todo.dto.request.CommentRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.CommentResponse;
import com.todo.entity.User;
import com.todo.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@RestController
@RequestMapping("/api/todos/{todoId}/comments")
@RequiredArgsConstructor
@Validated
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ApiResponse<List<CommentResponse>> list(@AuthenticationPrincipal User user,
                                                   @PathVariable Long todoId) {
        return ApiResponse.ok(commentService.listTree(todoId, user.getId()));
    }

    @PostMapping
    public ApiResponse<CommentResponse> create(@AuthenticationPrincipal User user,
                                               @PathVariable Long todoId,
                                               @Valid @RequestBody CommentRequest req) {
        return ApiResponse.ok(commentService.create(todoId, user.getId(), req), "评论成功");
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user,
                                    @PathVariable Long todoId,
                                    @PathVariable Long commentId) {
        commentService.delete(commentId, user.getId());
        return ApiResponse.ok(null, "评论已删除");
    }
}