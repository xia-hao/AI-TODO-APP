-- =============================================
-- Todo App 数据库初始化脚本（完整版）
-- 整合了 V1(基础字段)、V2(软删除时间)、V3(操作日志)、
--          Flyway V1(ai_messages)、Flyway V2(补充索引)
-- 用法: mysql -u root -p < init.sql
-- =============================================

CREATE DATABASE IF NOT EXISTS `todo_app`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `todo_app`;

-- =============================================
-- 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS `users` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`    BIGINT       DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`    BIGINT       DEFAULT NULL,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT(1)   DEFAULT 0,
    `username`     VARCHAR(50)  NOT NULL UNIQUE,
    `email`        VARCHAR(100) NOT NULL UNIQUE,
    `password`     VARCHAR(255) NOT NULL,
    `display_name` VARCHAR(100),
    INDEX `idx_email`   (`email`),
    INDEX `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 团队表
-- =============================================
CREATE TABLE IF NOT EXISTS `teams` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`   BIGINT       DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT       DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   DEFAULT 0,
    `name`        VARCHAR(100) NOT NULL,
    `description` VARCHAR(500),
    `invite_code` VARCHAR(20)  NOT NULL UNIQUE,
    `owner_id`    BIGINT       NOT NULL,
    INDEX `idx_invite_code` (`invite_code`),
    CONSTRAINT `fk_teams_owner` FOREIGN KEY (`owner_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 团队成员表
-- =============================================
CREATE TABLE IF NOT EXISTS `team_members` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`  BIGINT   DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`  BIGINT   DEFAULT NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT(1) DEFAULT 0,
    `team_id`    BIGINT   NOT NULL,
    `user_id`    BIGINT   NOT NULL,
    `role`       ENUM('OWNER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
    `joined_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uq_team_user` (`team_id`, `user_id`),
    CONSTRAINT `fk_team_members_team` FOREIGN KEY (`team_id`) REFERENCES `teams`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_team_members_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 项目表
-- =============================================
CREATE TABLE IF NOT EXISTS `projects` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`   BIGINT       DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT       DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   DEFAULT 0,
    `name`        VARCHAR(100) NOT NULL,
    `description` VARCHAR(500),
    `color`       VARCHAR(7)   DEFAULT '#409eff',
    `icon`        VARCHAR(50)  DEFAULT 'folder',
    `owner_id`    BIGINT       NOT NULL COMMENT '创建者',
    `is_archived` BOOLEAN      DEFAULT FALSE,
    `sort_order`  INT          DEFAULT 0,
    INDEX `idx_owner`   (`owner_id`),
    INDEX `idx_deleted` (`deleted`),
    CONSTRAINT `fk_projects_owner` FOREIGN KEY (`owner_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 项目-团队关联表（多对多）
-- =============================================
CREATE TABLE IF NOT EXISTS `project_teams` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`   BIGINT       DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT       DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   DEFAULT 0,
    `project_id`  BIGINT NOT NULL COMMENT '项目ID',
    `team_id`     BIGINT NOT NULL COMMENT '团队ID',
    INDEX `idx_project_teams_project` (`project_id`),
    INDEX `idx_project_teams_team` (`team_id`),
    UNIQUE KEY `uq_project_team` (`project_id`, `team_id`),
    CONSTRAINT `fk_project_teams_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_project_teams_team` FOREIGN KEY (`team_id`) REFERENCES `teams`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目-团队关联表';

-- =============================================
-- 分区表
-- =============================================
CREATE TABLE IF NOT EXISTS `sections` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`  BIGINT       DEFAULT NULL,
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`  BIGINT       DEFAULT NULL,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT(1)   DEFAULT 0,
    `project_id` BIGINT       NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    `sort_order` INT          DEFAULT 0,
    INDEX `idx_project` (`project_id`),
    CONSTRAINT `fk_sections_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 待办表
-- =============================================
CREATE TABLE IF NOT EXISTS `todos` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`    BIGINT       DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`    BIGINT       DEFAULT NULL,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT(1)   DEFAULT 0,
    `text`         VARCHAR(500) NOT NULL,
    `completed`    BOOLEAN      NOT NULL DEFAULT FALSE,
    `category`     ENUM('工作','生活','学习','其他') NOT NULL DEFAULT '其他',
    `priority`     ENUM('high','medium','low')       NOT NULL DEFAULT 'medium',
    `due_date`     DATE,
    `sort_order`   INT          NOT NULL DEFAULT 0,
    `owner_id`     BIGINT       NOT NULL,
    `team_id`      BIGINT,
    `assignee_id`  BIGINT,
    `project_id`   BIGINT       NOT NULL,
    `section_id`   BIGINT       NOT NULL,
    `deleted_time` DATETIME     NULL COMMENT '软删除时间',
    INDEX `idx_owner`             (`owner_id`),
    INDEX `idx_team`              (`team_id`),
    INDEX `idx_assignee`          (`assignee_id`),
    INDEX `idx_order`             (`owner_id`, `team_id`, `sort_order`),
    INDEX `idx_project`           (`project_id`),
    INDEX `idx_section`           (`section_id`),
    INDEX `idx_deleted`           (`deleted`),
    INDEX `idx_todos_deleted_time` (`deleted_time`),
    CONSTRAINT `fk_todos_owner`    FOREIGN KEY (`owner_id`)    REFERENCES `users`(`id`)    ON DELETE CASCADE,
    CONSTRAINT `fk_todos_team`     FOREIGN KEY (`team_id`)     REFERENCES `teams`(`id`)    ON DELETE SET NULL,
    CONSTRAINT `fk_todos_assignee` FOREIGN KEY (`assignee_id`) REFERENCES `users`(`id`)    ON DELETE SET NULL,
    CONSTRAINT `fk_todos_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_todos_section`  FOREIGN KEY (`section_id`)  REFERENCES `sections`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 子任务表
-- =============================================
CREATE TABLE IF NOT EXISTS `subtasks` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`   BIGINT       DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT       DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   DEFAULT 0,
    `todo_id`     BIGINT       NOT NULL COMMENT '所属任务',
    `text`        VARCHAR(500) NOT NULL,
    `completed`   BOOLEAN      DEFAULT FALSE,
    `sort_order`  INT          DEFAULT 0,
    `assignee_id` BIGINT       COMMENT '指定人',
    `due_date`    DATE         COMMENT '截止日期',
    INDEX `idx_todo`   (`todo_id`),
    INDEX `idx_deleted` (`deleted`),
    CONSTRAINT `fk_subtasks_todo`      FOREIGN KEY (`todo_id`)      REFERENCES `todos`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_subtasks_assignee`  FOREIGN KEY (`assignee_id`)  REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 标签表
-- =============================================
CREATE TABLE IF NOT EXISTS `tags` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`  BIGINT      DEFAULT NULL,
    `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`  BIGINT      DEFAULT NULL,
    `update_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT(1)  DEFAULT 0,
    `name`       VARCHAR(50) NOT NULL,
    `color`      VARCHAR(7)  DEFAULT '#909399',
    `owner_id`   BIGINT      COMMENT '创建者（NULL=系统内置标签）',
    `team_id`    BIGINT      COMMENT '团队标签',
    `project_id` BIGINT      COMMENT '所属项目（项目标签）',
    UNIQUE KEY `uq_tag_team` (`name`, `team_id`, `project_id`),
    CONSTRAINT `fk_tags_owner`   FOREIGN KEY (`owner_id`)   REFERENCES `users`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tags_team`    FOREIGN KEY (`team_id`)    REFERENCES `teams`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tags_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 待办-标签关联表
-- =============================================
CREATE TABLE IF NOT EXISTS `todo_tags` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `todo_id` BIGINT NOT NULL,
    `tag_id`  BIGINT NOT NULL,
    UNIQUE KEY `uq_todo_tag` (`todo_id`, `tag_id`),
    CONSTRAINT `fk_todo_tags_todo` FOREIGN KEY (`todo_id`) REFERENCES `todos`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_todo_tags_tag`  FOREIGN KEY (`tag_id`)  REFERENCES `tags`(`id`)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 评论表
-- =============================================
CREATE TABLE IF NOT EXISTS `comments` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`  BIGINT   DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`  BIGINT   DEFAULT NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT(1) DEFAULT 0,
    `todo_id`    BIGINT   NOT NULL,
    `user_id`    BIGINT   NOT NULL,
    `content`    TEXT     NOT NULL,
    `parent_id`  BIGINT   COMMENT '父评论ID，支持嵌套回复',
    INDEX `idx_todo`   (`todo_id`),
    INDEX `idx_deleted` (`deleted`),
    CONSTRAINT `fk_comments_todo`   FOREIGN KEY (`todo_id`)   REFERENCES `todos`(`id`)    ON DELETE CASCADE,
    CONSTRAINT `fk_comments_user`   FOREIGN KEY (`user_id`)   REFERENCES `users`(`id`)    ON DELETE CASCADE,
    CONSTRAINT `fk_comments_parent` FOREIGN KEY (`parent_id`) REFERENCES `comments`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 附件表
-- =============================================
CREATE TABLE IF NOT EXISTS `attachments` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`  BIGINT       DEFAULT NULL,
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`  BIGINT       DEFAULT NULL,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT(1)   DEFAULT 0,
    `todo_id`    BIGINT       NOT NULL,
    `user_id`    BIGINT       NOT NULL,
    `file_name`  VARCHAR(255) NOT NULL,
    `file_path`  VARCHAR(500) NOT NULL,
    `file_size`  BIGINT       DEFAULT 0,
    `mime_type`  VARCHAR(100),
    INDEX `idx_todo`   (`todo_id`),
    INDEX `idx_deleted` (`deleted`),
    CONSTRAINT `fk_attachments_todo` FOREIGN KEY (`todo_id`) REFERENCES `todos`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_attachments_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 提醒表
-- =============================================
CREATE TABLE IF NOT EXISTS `reminders` (
    `id`         BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`  BIGINT    DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`  BIGINT    DEFAULT NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT(1) DEFAULT 0,
    `todo_id`    BIGINT    NOT NULL,
    `user_id`    BIGINT    NOT NULL,
    `remind_at`  DATETIME  NOT NULL,
    `is_sent`    BOOLEAN   DEFAULT FALSE,
    `sent_at`    DATETIME,
    INDEX `idx_pending` (`remind_at`, `is_sent`),
    INDEX `idx_deleted` (`deleted`),
    CONSTRAINT `fk_reminders_todo` FOREIGN KEY (`todo_id`) REFERENCES `todos`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_reminders_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 通知表
-- =============================================
CREATE TABLE IF NOT EXISTS `notifications` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`  BIGINT       DEFAULT NULL,
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`  BIGINT       DEFAULT NULL,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT(1)   DEFAULT 0,
    `user_id`    BIGINT       NOT NULL COMMENT '接收者',
    `type`       VARCHAR(50)  NOT NULL COMMENT 'COMMENT_MENTION/ASSIGNEE/TODO_REMIND',
    `title`      VARCHAR(200) NOT NULL,
    `content`    VARCHAR(500),
    `target_url` VARCHAR(500) COMMENT '点击跳转链接',
    `is_read`    BOOLEAN      DEFAULT FALSE,
    INDEX `idx_user_read` (`user_id`, `is_read`, `create_time` DESC),
    INDEX `idx_deleted`   (`deleted`),
    CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 操作日志表
-- =============================================
CREATE TABLE IF NOT EXISTS `activity_logs` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`   BIGINT       DEFAULT NULL COMMENT '操作人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT       DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   DEFAULT 0,
    `project_id`  BIGINT       COMMENT '所属项目（NULL=全局操作）',
    `user_id`     BIGINT       NOT NULL COMMENT '操作人',
    `action`      VARCHAR(50)  NOT NULL COMMENT 'create/update/delete/complete/restore/move',
    `target_type` VARCHAR(50)  NOT NULL COMMENT 'Todo/Project/Section',
    `target_id`   BIGINT       NOT NULL COMMENT '操作目标ID',
    `detail`      VARCHAR(500) COMMENT '变更详情',
    INDEX `idx_project_time` (`project_id`, `create_time` DESC),
    INDEX `idx_user_time`    (`user_id`, `create_time` DESC),
    INDEX `idx_target`       (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 报告表
-- =============================================
CREATE TABLE IF NOT EXISTS `reports` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`    BIGINT       DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`    BIGINT       DEFAULT NULL,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT(1)   DEFAULT 0,
    `user_id`      BIGINT       NOT NULL COMMENT '归属用户',
    `type`         VARCHAR(10)  NOT NULL COMMENT 'DAILY / WEEKLY',
    `scope`        VARCHAR(10)  NOT NULL DEFAULT 'SELF' COMMENT 'SELF=执行人报告, TEAM=领导报告',
    `team_id`      BIGINT       COMMENT 'NULL=个人报告, 非NULL=团队报告',
    `title`        VARCHAR(200) NOT NULL COMMENT '标题，如"2026-05-13 日报"',
    `preview`      VARCHAR(500) COMMENT '简短摘要（通知预览用）',
    `content`      TEXT         COMMENT 'Markdown 全文（邮件用）',
    `json_data`    JSON         COMMENT '结构化数据（前端渲染用）',
    `period_start` DATE         NOT NULL COMMENT '统计起始日期',
    `period_end`   DATE         NOT NULL COMMENT '统计截止日期',
    UNIQUE KEY `uq_user_type_scope_team_period` (`user_id`, `type`, `scope`, `team_id`, `period_end`),
    INDEX `idx_user_type_time` (`user_id`, `type`, `create_time` DESC),
    INDEX `idx_deleted` (`deleted`),
    CONSTRAINT `fk_reports_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- AI 对话消息表
-- =============================================
CREATE TABLE IF NOT EXISTS `ai_messages` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `create_by`   BIGINT       DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT       DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   DEFAULT 0,
    `session_id`  VARCHAR(64)  NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `role`        VARCHAR(16)  NOT NULL COMMENT 'user/assistant',
    `content`     TEXT         NOT NULL,
    INDEX `idx_session` (`session_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息';

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

-- =============================================
-- 补充索引（复合索引 & 全文索引）
-- =============================================
CREATE INDEX IF NOT EXISTS idx_todos_project_lookup ON todos(project_id, deleted, completed, sort_order);
CREATE FULLTEXT INDEX IF NOT EXISTS idx_todos_text ON todos(text);
CREATE INDEX IF NOT EXISTS idx_todos_team_assignee ON todos(team_id, assignee_id);
CREATE INDEX IF NOT EXISTS idx_todos_due_date ON todos(due_date);
CREATE INDEX IF NOT EXISTS idx_tags_project ON tags(project_id);
CREATE INDEX IF NOT EXISTS idx_tags_team ON tags(team_id);
CREATE INDEX IF NOT EXISTS idx_subtasks_todo_order ON subtasks(todo_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_reminders_todo ON  (todo_id);
CREATE INDEX IF NOT EXISTS idx_todo_tags_tag ON todo_tags(tag_id);
