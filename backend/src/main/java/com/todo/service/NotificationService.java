package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.todo.dto.response.NotificationResponse;
import com.todo.entity.Notification;
import com.todo.entity.User;
import com.todo.mapper.NotificationMapper;
import com.todo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserMapper userMapper;
    private final MailService mailService;

    @Transactional
    public void create(Long userId, String type, String title, String content, String targetUrl) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setTargetUrl(targetUrl);
        n.setIsRead(false);
        notificationMapper.insert(n);

        NotificationResponse resp = toResponse(n);
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", resp);

        // 异步发送邮件
        User user = userMapper.selectById(userId);
        if (user != null && user.getEmail() != null) {
            mailService.send(user.getEmail(), title, content);
        }
    }

    /** 当评论中 @提及某人时调用 */
    @Transactional
    public void mentionInComment(Long todoId, Long projectId, Long fromUserId, String mentionedUsername) {
        User mentionedUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getDisplayName, mentionedUsername));
        if (mentionedUser == null  || mentionedUser.getId().equals(fromUserId)) return;
        String targetUrl = "/projects/" + projectId + "?todo=" + todoId;
        create(mentionedUser.getId(),
                "COMMENT_MENTION",
                "有人@了你",
                "在项目 #" + projectId + " 的待办 #" + todoId + " 的评论中提到了你",
                targetUrl);
    }

    public List<NotificationResponse> listUnread(Long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false)
                        .orderByDesc(Notification::getCreateTime))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public int unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)).intValue();
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, true));
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true));
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setId(n.getId());
        r.setType(n.getType());
        r.setTitle(n.getTitle());
        r.setContent(n.getContent());
        r.setTargetUrl(n.getTargetUrl());
        r.setIsRead(n.getIsRead());
        r.setCreateTime(n.getCreateTime());
        return r;
    }
}