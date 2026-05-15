package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.request.CommentRequest;
import com.todo.dto.response.CommentResponse;
import com.todo.entity.Comment;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.exception.AppException;
import com.todo.mapper.CommentMapper;
import com.todo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final TodoService todoService;
    private final NotificationService notificationService;

    /**
     * 获取任务的所有评论（树形结构）
     */
    public List<CommentResponse> listTree(Long todoId, Long userId) {
        // 校验访问权限
        todoService.getAndAssertAccess(todoId, userId, false);

        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTodoId, todoId)
                        .orderByAsc(Comment::getCreateTime));

        // 批量加载用户信息
        java.util.Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        java.util.Map<Long, User> userMap = userIds.isEmpty() ? java.util.Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .filter(u -> u != null)
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 填充用户信息
        List<CommentResponse> responses = comments.stream().map(c -> {
            CommentResponse r = new CommentResponse();
            r.setId(c.getId());
            r.setTodoId(c.getTodoId());
            r.setUserId(c.getUserId());
            r.setContent(c.getContent());
            r.setParentId(c.getParentId());
            r.setCreateTime(c.getCreateTime());
            r.setUpdateTime(c.getUpdateTime());
            User u = userMap.get(c.getUserId());
            if (u != null) {
                r.setUsername(u.getUsername());
                r.setDisplayName(u.getDisplayName());
            }
            return r;
        }).collect(Collectors.toList());

        // 构建树形结构：parentId == null 为根评论，children 存放回复
        Map<Long, CommentResponse> map = responses.stream()
                .collect(Collectors.toMap(CommentResponse::getId, r -> r));
        List<CommentResponse> roots = new ArrayList<>();
        for (CommentResponse r : responses) {
            if (r.getParentId() == null) {
                roots.add(r);
            } else {
                CommentResponse parent = map.get(r.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                    parent.getChildren().add(r);
                } else {
                    // 父评论可能已被删除，作为根处理
                    roots.add(r);
                }
            }
        }
        return roots;
    }

    @Transactional
    public CommentResponse create(Long todoId, Long userId, CommentRequest req) {
        // 校验对 todo 的访问权限
        todoService.getAndAssertAccess(todoId, userId, false);

        Comment comment = new Comment();
        comment.setTodoId(todoId);
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        comment.setParentId(req.getParentId());
        commentMapper.insert(comment);
        // 解析 @提及
        String content = req.getContent();
        Pattern pattern = Pattern.compile("@([\\w\\u4e00-\\u9fa5]{2,50})");
        Matcher matcher = pattern.matcher(content);
        Todo todo = todoService.getAndAssertAccess(todoId, userId, false);
        Long projectId = todo.getProjectId();
        while (matcher.find()) {
            String username = matcher.group(1);
            User mentionedUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getDisplayName, username));
            if (mentionedUser == null || mentionedUser.getId().equals(userId)) continue;

            if (todo.getProjectId() != null) {
                // 项目内的任务
                notificationService.mentionInComment(todoId, todo.getProjectId(), userId, username);
            } else {
                String targetUrl = "/?todo=" + todoId;
                notificationService.create(
                        mentionedUser.getId(),
                        "COMMENT_MENTION",
                        "有人@了你",
                        "在待办 #" + todoId + " 的评论中提到了你",
                        targetUrl
                );
            }
        }
        // 返回完整信息
        return toResponse(comment);
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) throw AppException.notFound("评论");
        if (!comment.getUserId().equals(userId)) throw AppException.forbidden();
        // 级联删除子评论（通过外键 ON DELETE CASCADE）
        commentMapper.deleteById(commentId);
    }

    private CommentResponse toResponse(Comment c) {
        CommentResponse r = new CommentResponse();
        r.setId(c.getId());
        r.setTodoId(c.getTodoId());
        r.setUserId(c.getUserId());
        r.setContent(c.getContent());
        r.setParentId(c.getParentId());
        r.setCreateTime(c.getCreateTime());
        r.setUpdateTime(c.getUpdateTime());
        User u = userMapper.selectById(c.getUserId());
        if (u != null) {
            r.setUsername(u.getUsername());
            r.setDisplayName(u.getDisplayName());
        }
        return r;
    }
}