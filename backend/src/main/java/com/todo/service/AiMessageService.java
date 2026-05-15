package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.entity.AiMessage;
import com.todo.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiMessageService {

    private final AiMessageMapper aiMessageMapper;

    public void saveMessage(Long userId, String sessionId, String role, String content) {
        AiMessage msg = new AiMessage();
        msg.setUserId(userId);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        aiMessageMapper.insert(msg);
    }

    public List<AiMessage> getMessages(String sessionId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getSessionId, sessionId)
               .orderByAsc(AiMessage::getCreateTime);
        return aiMessageMapper.selectList(wrapper);
    }
}
