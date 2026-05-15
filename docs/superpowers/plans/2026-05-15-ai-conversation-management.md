# AI 助手会话管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ChatGPT-like conversation management to the AI assistant — list, create, switch, rename, delete conversations, persisted across browser sessions.

**Architecture:** New `ai_conversations` table with `BaseEntity` inheritance. Backend CRUD endpoints under `AiController`. New ai-service `POST /api/generate-title` endpoint for automatic title generation. Frontend drawer splits into two-column layout: left conversation list, right chat area.

**Tech Stack:** Spring Boot + MyBatis-Plus (backend), Python FastAPI + OpenAI SDK (ai-service), Vue 3 + Pinia + TypeScript (frontend)

---

### Task 1: Database — Create `ai_conversations` table

**Files:**
- Modify: `backend/src/main/resources/db/init.sql`

- [ ] **Step 1: Add table DDL to init.sql**

```sql
-- =============================================
-- AI 会话表
-- =============================================
CREATE TABLE IF NOT EXISTS `ai_conversations` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`   BIGINT       DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT       DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   DEFAULT 0,
    `user_id`     BIGINT       NOT NULL,
    `title`       VARCHAR(200) NOT NULL DEFAULT '新对话',
    INDEX `idx_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话';
```

Insert this after the `ai_messages` table definition (line ~367 in current init.sql).

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/db/init.sql
git commit -m "feat: add ai_conversations table"
```

---

### Task 2: Backend — AiConversation entity & mapper

**Files:**
- Create: `backend/src/main/java/com/todo/entity/AiConversation.java`
- Create: `backend/src/main/java/com/todo/mapper/AiConversationMapper.java`

- [ ] **Step 1: Create entity class**

`backend/src/main/java/com/todo/entity/AiConversation.java`:

```java
package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversations")
public class AiConversation extends BaseEntity {
    private Long userId;
    private String title;
}
```

- [ ] **Step 2: Create mapper interface**

`backend/src/main/java/com/todo/mapper/AiConversationMapper.java`:

```java
package com.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.todo.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/todo/entity/AiConversation.java \
       backend/src/main/java/com/todo/mapper/AiConversationMapper.java
git commit -m "feat: add AiConversation entity and mapper"
```

---

### Task 3: Backend — AiConversationService

**Files:**
- Create: `backend/src/main/java/com/todo/service/AiConversationService.java`

- [ ] **Step 1: Create service**

`backend/src/main/java/com/todo/service/AiConversationService.java`:

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/todo/service/AiConversationService.java
git commit -m "feat: add AiConversationService"
```

---

### Task 4: Backend — Add conversation endpoints to AiController

**Files:**
- Modify: `backend/src/main/java/com/todo/controller/AiController.java`

- [ ] **Step 1: Read current AiController.java**

```bash
cat backend/src/main/java/com/todo/controller/AiController.java
```

- [ ] **Step 2: Add conversation endpoints**

Current controller has `chat` and `createSession` methods. Add the following imports and endpoints:

```java
import com.todo.service.AiConversationService;
import com.todo.dto.request.RenameRequest;
import com.todo.entity.AiConversation;
import com.todo.entity.AiMessage;
import java.util.Map;
import java.util.HashMap;
```

Inject `AiConversationService`:

```java
private final AiConversationService aiConversationService;
```

Add endpoints:

```java
@GetMapping("/conversations")
public ApiResponse<List<AiConversation>> listConversations(@AuthenticationPrincipal User user) {
    return ApiResponse.ok(aiConversationService.listByUser(user.getId()));
}

@PostMapping("/conversations")
public ApiResponse<AiConversation> createConversation(@AuthenticationPrincipal User user) {
    return ApiResponse.ok(aiConversationService.create(user.getId()));
}

@DeleteMapping("/conversations/{id}")
public ApiResponse<Void> deleteConversation(@AuthenticationPrincipal User user,
                                             @PathVariable Long id) {
    aiConversationService.delete(id, user.getId());
    return ApiResponse.ok(null, "已删除");
}

@PutMapping("/conversations/{id}/rename")
public ApiResponse<Void> renameConversation(@AuthenticationPrincipal User user,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
    String title = body.get("title");
    if (title == null || title.trim().isEmpty()) {
        throw new AppException("标题不能为空", HttpStatus.BAD_REQUEST);
    }
    aiConversationService.rename(id, user.getId(), title.trim());
    return ApiResponse.ok(null, "已重命名");
}

@GetMapping("/conversations/{id}/messages")
public ApiResponse<List<AiMessage>> getConversationMessages(@AuthenticationPrincipal User user,
                                                             @PathVariable Long id) {
    return ApiResponse.ok(aiConversationService.getMessages(id, user.getId()));
}
```

Add `import com.todo.exception.AppException;` and `import org.springframework.http.HttpStatus;` if not already present. Add `import java.util.Map;` as needed.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/todo/controller/AiController.java
git commit -m "feat: add conversation CRUD endpoints"
```

---

### Task 5: Backend — Adjust AiService to accept conversation_id and generate title

**Files:**
- Modify: `backend/src/main/java/com/todo/service/AiService.java`
- Modify: `backend/src/main/java/com/todo/dto/request/AiChatRequest.java`

- [ ] **Step 1: Add `conversationId` to AiChatRequest**

Read current request DTO:

```bash
cat backend/src/main/java/com/todo/dto/request/AiChatRequest.java
```

Add field:

```java
private Long conversationId;
```

- [ ] **Step 2: Modify AiService.chatStream to handle conversation_id**

Current `AiService.java` creates a new session_id via `AiService.createSession()` or uses the one from the request. Change it to:

1. Accept `conversationId` from the request
2. Use `conversationId` as the `session_id` (convert to string) when forwarding to ai-service
3. After receiving the first user message, if the conversation title is "新对话", asynchronously call ai-service to generate a title

Modified `chatStream` method changes:

```java
// Use conversation ID as session_id
String sessionId;
if (request.getConversationId() != null) {
    sessionId = String.valueOf(request.getConversationId());
} else {
    // fallback: use existing sessionId or generate new one
    sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString().replace("-", "");
}

// After saving user message and before forwarding to ai-service:
// Check if this is the first message and we need to generate a title
if (request.getConversationId() != null) {
    final Long convId = request.getConversationId();
    final String userMessage = message;
    // Async title generation
    executor.execute(() -> {
        try {
            String title = generateTitle(userMessage);
            if (title != null && !title.isEmpty()) {
                aiConversationService.updateTitle(convId, title);
            }
        } catch (Exception e) {
            log.warn("Failed to generate title for conversation {}", convId, e);
        }
    });
}
```

Add the `generateTitle` method:

```java
private String generateTitle(String message) {
    try {
        URL url = new URL(aiConfig.getServiceUrl() + "/api/generate-title");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Api-Key", aiConfig.getInternalApiKey() != null ? aiConfig.getInternalApiKey() : "");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(objectMapper.writeValueAsBytes(body));
            os.flush();
        }

        int status = conn.getResponseCode();
        if (status == 200) {
            Map<String, Object> resp = objectMapper.readValue(conn.getInputStream(), Map.class);
            Object title = resp.get("title");
            return title != null ? title.toString() : null;
        }
    } catch (Exception e) {
        log.warn("Title generation failed", e);
    }
    return null;
}
```

Add `AiConversationService` dependency:

```java
private final AiConversationService aiConversationService;
```

Inject via constructor (currently `@RequiredArgsConstructor` + `final` field).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/todo/service/AiService.java \
       backend/src/main/java/com/todo/dto/request/AiChatRequest.java
git commit -m "feat: support conversation_id in AI chat, async title generation"
```

---

### Task 6: ai-service — Add title generation endpoint

**Files:**
- Modify: `ai-service/app/routes/chat.py`

- [ ] **Step 1: Add generate-title endpoint**

Add to `chat.py`:

```python
@router.post("/generate-title")
async def generate_title(request: GenerateTitleRequest):
    settings = _s()
    llm_client = _llm()

    messages = [
        {"role": "system", "content": "根据用户的输入，生成一个简短的对话标题（不超过 20 个字），直接返回标题内容，不要解释。"},
        {"role": "user", "content": request.message},
    ]

    full_title = ""
    for chunk in llm_client.chat_stream(messages, []):
        if chunk.get("type") == "text":
            full_title += chunk.get("content", "")
        elif chunk.get("type") == "done":
            break

    title = full_title.strip().strip('"\'「」').strip()[:50] or "新对话"
    return {"title": title}
```

Add the request model:

```python
class GenerateTitleRequest(BaseModel):
    message: str
```

- [ ] **Step 2: Commit**

```bash
git add ai-service/app/routes/chat.py
git commit -m "feat: add title generation endpoint"
```

---

### Task 7: Frontend — Refactor ai store with conversation support

**Files:**
- Modify: `frontend/src/stores/ai.ts`
- Modify: `frontend/src/api/ai.ts`

- [ ] **Step 1: Add conversation API to frontend api/ai.ts**

Add new API functions:

```typescript
export interface Conversation {
  id: number
  title: string
  updateTime: string
}

export function listConversations() {
  return http.get<{ success: boolean; data: Conversation[] }>('/ai/conversations')
}

export function createConversation() {
  return http.post<{ success: boolean; data: Conversation }>('/ai/conversations')
}

export function deleteConversation(id: number) {
  return http.delete(`/ai/conversations/${id}`)
}

export function renameConversation(id: number, title: string) {
  return http.put(`/ai/conversations/${id}/rename`, { title })
}

export function getConversationMessages(id: number) {
  return http.get<{ success: boolean; data: any[] }>(`/ai/conversations/${id}/messages`)
}
```

- [ ] **Step 2: Refactor ai store**

Full rewrite of `frontend/src/stores/ai.ts`:

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  createSession, chatStream, listConversations,
  createConversation, deleteConversation, renameConversation,
  getConversationMessages
} from '@/api/ai'
import type { Conversation } from '@/api/ai'
import { ElMessage, ElMessageBox } from 'element-plus'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  id: string
}

export const useAiStore = defineStore('ai', () => {
  const conversations = ref<Conversation[]>([])
  const currentId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const error = ref('')
  const showPanel = ref(false)

  const currentConversation = computed(() =>
    conversations.value.find(c => c.id === currentId.value) || null
  )

  async function fetchConversations() {
    try {
      const res = await listConversations()
      conversations.value = res.data.data || []
      // Auto-select first if none selected
      if (!currentId.value && conversations.value.length > 0) {
        await selectConversation(conversations.value[0].id)
      }
    } catch {
      ElMessage.error('加载会话列表失败')
    }
  }

  async function selectConversation(id: number) {
    currentId.value = id
    messages.value = []
    try {
      const res = await getConversationMessages(id)
      const data = res.data.data || []
      messages.value = data.map((m: any, i: number) => ({
        role: m.role,
        content: m.content || '',
        id: `msg-${id}-${i}`,
      }))
    } catch {
      ElMessage.error('加载历史消息失败')
    }
  }

  async function newConversation() {
    try {
      const res = await createConversation()
      const conv = res.data.data
      conversations.value.unshift(conv)
      currentId.value = conv.id
      messages.value = []
    } catch {
      ElMessage.error('创建会话失败')
    }
  }

  async function removeConversation(id: number) {
    try {
      await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await deleteConversation(id)
      conversations.value = conversations.value.filter(c => c.id !== id)
      if (currentId.value === id) {
        currentId.value = null
        messages.value = []
        if (conversations.value.length > 0) {
          await selectConversation(conversations.value[0].id)
        }
      }
    } catch {
      // cancelled or error
    }
  }

  async function renameConversationAction(id: number, title: string) {
    try {
      await renameConversation(id, title)
      const conv = conversations.value.find(c => c.id === id)
      if (conv) conv.title = title
    } catch {
      ElMessage.error('重命名失败')
    }
  }

  async function sendMessage(text: string) {
    if (!text.trim() || loading.value) return

    const userMsg: ChatMessage = {
      role: 'user', content: text, id: Date.now().toString(),
    }
    messages.value.push(userMsg)
    loading.value = true
    error.value = ''

    const assistantMsg: ChatMessage = {
      role: 'assistant', content: '', id: (Date.now() + 1).toString(),
    }
    messages.value.push(assistantMsg)

    try {
      const stream = chatStream({
        message: text,
        sessionId: currentId.value ? String(currentId.value) : undefined,
      })

      await stream.start(
        (data: any) => {
          if (data.type === 'text' || data.type === 'token') {
            assistantMsg.content += data.content || ''
          } else if (data.type === 'tool_call') {
            assistantMsg.content += `\n[正在调用工具: ${data.name}...]\n`
          }
        },
        () => {
          loading.value = false
          if (!assistantMsg.content) {
            assistantMsg.content = 'AI 服务暂不可用，请稍后重试。'
          }
        },
        (err: any) => {
          loading.value = false
          error.value = err.message || '请求失败'
          assistantMsg.content = err.message || '抱歉，我遇到了问题，请稍后再试。'
        }
      )
    } catch (err: any) {
      loading.value = false
      error.value = err.message || '请求失败'
      assistantMsg.content = err.message || '抱歉，我遇到了问题，请稍后再试。'
    }
  }

  function togglePanel() {
    showPanel.value = !showPanel.value
    if (showPanel.value && conversations.value.length === 0) {
      fetchConversations()
    }
  }

  return {
    conversations, currentId, messages, loading, error, showPanel,
    currentConversation,
    fetchConversations, selectConversation, newConversation,
    removeConversation, renameConversationAction, sendMessage, togglePanel,
  }
})
```

- [ ] **Step 3: Remove unused imports from api/ai.ts**

Remove `import { useAuthStore } from '@/stores/auth'` if no longer needed. Keep the rest unchanged (ensureFreshToken logic still needed).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/ai.ts frontend/src/stores/ai.ts
git commit -m "feat: refactor AI store with conversation management"
```

---

### Task 8: Frontend — ConversationList component

**Files:**
- Create: `frontend/src/components/ai/ConversationList.vue`

- [ ] **Step 1: Create ConversationList.vue**

```vue
<template>
  <div class="conversation-list">
    <div class="list-header">
      <span class="list-title">会话</span>
      <el-button :icon="Plus" circle size="small" @click="createNew" :disabled="loading" />
    </div>
    <div class="list-items" v-if="conversations.length > 0">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="list-item"
        :class="{ active: conv.id === currentId }"
        @click="select(conv.id)"
      >
        <div class="item-content">
          <div class="item-title">{{ conv.title }}</div>
          <div class="item-time">{{ formatTime(conv.updateTime) }}</div>
        </div>
        <div class="item-actions" @click.stop>
          <el-button :icon="Edit" text size="small" @click="startRename(conv)" />
          <el-button :icon="Delete" text size="small" type="danger" @click="remove(conv.id)" />
        </div>
      </div>
    </div>
    <div v-else class="list-empty">
      <p>暂无会话</p>
    </div>

    <!-- Rename dialog -->
    <el-dialog v-model="renameVisible" title="重命名" width="350px" :close-on-click-modal="false">
      <el-input v-model="renameValue" maxlength="50" show-word-limit @keyup.enter="confirmRename" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRename">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { useAiStore } from '@/stores/ai'
import { storeToRefs } from 'pinia'
import type { Conversation } from '@/api/ai'

const aiStore = useAiStore()
const { conversations, currentId, loading } = storeToRefs(aiStore)

const renameVisible = ref(false)
const renameValue = ref('')
const renameTarget = ref<Conversation | null>(null)

function createNew() {
  aiStore.newConversation()
}

function select(id: number) {
  if (id !== currentId.value) {
    aiStore.selectConversation(id)
  }
}

function remove(id: number) {
  aiStore.removeConversation(id)
}

function startRename(conv: Conversation) {
  renameTarget.value = conv
  renameValue.value = conv.title
  renameVisible.value = true
}

function confirmRename() {
  if (renameTarget.value && renameValue.value.trim()) {
    aiStore.renameConversationAction(renameTarget.value.id, renameValue.value.trim())
  }
  renameVisible.value = false
  renameTarget.value = null
}

function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 86400000) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (diff < 172800000) return '昨天'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
.conversation-list {
  width: 220px;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-blank);
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.list-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.list-items {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s;
}

.list-item:hover,
.list-item.active {
  background: var(--el-fill-color-light);
}

.item-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.item-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.item-actions {
  display: none;
  gap: 2px;
  flex-shrink: 0;
}

.list-item:hover .item-actions {
  display: flex;
}

.list-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/ai/ConversationList.vue
git commit -m "feat: add ConversationList component"
```

---

### Task 9: Frontend — Update AiChatPanel with two-column layout

**Files:**
- Modify: `frontend/src/components/ai/AiChatPanel.vue`

- [ ] **Step 1: Rewrite AiChatPanel.vue**

Wrap existing chat content in a right column, add ConversationList in left column:

```vue
<template>
  <div class="ai-chat-panel">
    <div class="panel-body">
      <ConversationList />
      <div class="chat-area">
        <div class="chat-header">
          <span class="chat-title">AI 助手</span>
          <el-button :icon="Delete" text @click="clearMessages" :disabled="loading" />
        </div>

        <div class="chat-messages" ref="messagesRef">
          <div v-if="messages.length === 0" class="empty-state">
            <div class="empty-icon">🤖</div>
            <p>你好！我是你的 AI 任务助手。</p>
            <p class="hint">我可以帮你：</p>
            <ul>
              <li>创建、查询、管理任务</li>
              <li>生成日报/周报</li>
              <li>查看统计数据和项目信息</li>
            </ul>
            <p class="hint-bottom">试试对我说"帮我创建任务"或"查看我的任务统计"</p>
          </div>

          <div v-for="(msg, index) in messages" :key="msg.id" class="message-row" :class="msg.role">
            <div class="avatar">
              {{ msg.role === 'assistant' ? '🤖' : '👤' }}
            </div>
            <div v-if="msg.content" class="bubble" v-html="renderContent(msg.content)" />
            <div v-else-if="msg.role === 'assistant' && index === messages.length - 1 && loading" class="bubble loading-dots">
              <span class="dot" /><span class="dot" /><span class="dot" />
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            :placeholder="loading ? 'AI 正在思考...' : '输入你的问题...'"
            :disabled="loading"
            @keydown.enter.prevent="handleSend"
            resize="none"
          />
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="loading"
            :disabled="!inputText.trim()"
            @click="handleSend"
            class="send-btn"
          />
        </div>

        <!-- Rename dialog -->
        <el-dialog v-model="renameVisible" title="重命名" width="350px">
          <el-input v-model="renameValue" maxlength="50" show-word-limit @keyup.enter="confirmRename" />
          <template #footer>
            <el-button @click="renameVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmRename">确定</el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Delete, Promotion } from '@element-plus/icons-vue'
import { useAiStore } from '@/stores/ai'
import ConversationList from './ConversationList.vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const aiStore = useAiStore()
const { messages, loading } = storeToRefs(aiStore)
const inputText = ref('')
const messagesRef = ref<HTMLElement | null>(null)

const renameVisible = ref(false)
const renameValue = ref('')

watch(() => messages.value.length, () => scrollToBottom())
watch(loading, () => scrollToBottom())

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text || loading.value) return
  inputText.value = ''
  aiStore.sendMessage(text)
}

function clearMessages() {
  inputText.value = ''
  aiStore.clearMessages()
}

function renderContent(content: string): string {
  if (!content) return ''
  const rawHtml = marked.parse(content, { breaks: true }) as string
  return DOMPurify.sanitize(rawHtml)
}
</script>

<style scoped>
.ai-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color);
}

.panel-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 32px 16px;
}

.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-state p { margin: 4px 0; font-size: 14px; }
.empty-state ul { text-align: left; display: inline-block; margin: 8px auto; padding-left: 20px; font-size: 13px; line-height: 1.8; }
.hint-bottom { margin-top: 12px !important; font-size: 12px !important; opacity: 0.7; }

.message-row { display: flex; gap: 8px; max-width: 100%; }
.message-row.user { flex-direction: row-reverse; }

.avatar { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0; }

.bubble { max-width: 80%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; word-break: break-word; }
.message-row.assistant .bubble { background: var(--el-fill-color-light); color: var(--el-text-color-primary); border-bottom-left-radius: 4px; }
.message-row.user .bubble { background: var(--el-color-primary-light-3); color: #fff; border-bottom-right-radius: 4px; }

.loading-dots { display: flex; gap: 4px; align-items: center; padding: 14px 18px !important; }
.dot { width: 8px; height: 8px; background: var(--el-text-color-secondary); border-radius: 50%; animation: bounce 1.4s infinite ease-in-out both; }
.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
.dot:nth-child(3) { animation-delay: 0s; }

@keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1); } }

.chat-input { display: flex; align-items: flex-end; gap: 8px; padding: 12px 16px; border-top: 1px solid var(--el-border-color-light); }
.chat-input :deep(.el-textarea__inner) { border-radius: 8px; font-size: 14px; }
.send-btn { flex-shrink: 0; height: 36px; width: 36px; padding: 0; font-size: 18px; }

code { background: var(--el-fill-color-darker); padding: 2px 6px; border-radius: 4px; font-size: 13px; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/ai/AiChatPanel.vue
git commit -m "feat: two-column layout with conversation list in AI chat panel"
```

---

### Task 10: Remove obsolete frontend session creation

**Files:**
- Modify: `frontend/src/stores/ai.ts` (confirm `createSession` is no longer called)
- Modify: `frontend/src/api/ai.ts` (keep `chatStream`, remove `createSession` if unused)

- [ ] **Step 1: Remove `createSession` from api/ai.ts**

Remove the `createSession` function and its import from the new store. The store now uses `createConversation` instead.

- [ ] **Step 2: Verify no references to `createSession` remain in frontend**

```bash
grep -r "createSession" frontend/src --include="*.ts" --include="*.vue"
```

Expected: no output (all references removed).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/ai.ts
git commit -m "refactor: remove obsolete createSession API call"
```

---

## Self-Review Checklist

- [ ] **Spec coverage**: Every requirement from the decision doc has a corresponding task (DB → entity → service → API → ai-service → frontend store → frontend components)
- [ ] **No placeholders**: All code blocks contain complete, compilable code
- [ ] **Type consistency**: Entity field names match across service, mapper, and controller
