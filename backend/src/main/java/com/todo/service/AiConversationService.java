package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.entity.AiConversation;
import com.todo.entity.AiMessage;
import com.todo.exception.AppException;
import com.todo.mapper.AiConversationMapper;
import com.todo.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiConversationService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper aiMessageMapper;

    public List<AiConversation> listByUser(Long userId) {
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConversation::getUserId, userId)
               .orderByDesc(AiConversation::getUpdateTime);
        return conversationMapper.selectList(wrapper);
    }

    public AiConversation create(Long userId) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle("新对话");
        conversationMapper.insert(conv);
        return conv;
    }

    public AiConversation getById(Long id, Long userId) {
        AiConversation conv = conversationMapper.selectById(id);
        if (conv == null || !userId.equals(conv.getUserId())) {
            throw new AppException("会话不存在", HttpStatus.NOT_FOUND);
        }
        return conv;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        AiConversation conv = getById(id, userId);
        conversationMapper.deleteById(id);
    }

    public void rename(Long id, Long userId, String title) {
        AiConversation conv = getById(id, userId);
        conv.setTitle(title);
        conversationMapper.updateById(conv);
    }

    public void updateTitle(Long id, String title) {
        AiConversation conv = conversationMapper.selectById(id);
        if (conv != null) {
            conv.setTitle(title);
            conversationMapper.updateById(conv);
        }
    }

    public List<AiMessage> getMessages(Long id, Long userId) {
        getById(id, userId); // verify ownership
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getSessionId, String.valueOf(id))
               .orderByAsc(AiMessage::getCreateTime);
        return aiMessageMapper.selectList(wrapper);
    }
}
