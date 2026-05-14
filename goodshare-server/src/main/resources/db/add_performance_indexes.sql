-- =====================================================
-- 性能优化索引脚本
-- 针对推荐服务查询路径和高频 Mapper 查询
-- 执行前请先备份数据库
-- MySQL 兼容的安全索引创建脚本（存储过程方式）
-- =====================================================

DROP PROCEDURE IF EXISTS create_idx;

DELIMITER $$

CREATE PROCEDURE create_idx()
BEGIN
    -- ==================== post_likes 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'post_likes' AND index_name = 'idx_post_likes_post_user' LIMIT 1) THEN
        CREATE UNIQUE INDEX idx_post_likes_post_user ON post_likes (post_id, user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'post_likes' AND index_name = 'idx_post_likes_user' LIMIT 1) THEN
        CREATE INDEX idx_post_likes_user ON post_likes (user_id);
    END IF;

    -- ==================== favorites 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'favorites' AND index_name = 'idx_favorites_post_user' LIMIT 1) THEN
        CREATE UNIQUE INDEX idx_favorites_post_user ON favorites (post_id, user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'favorites' AND index_name = 'idx_favorites_user' LIMIT 1) THEN
        CREATE INDEX idx_favorites_user ON favorites (user_id);
    END IF;

    -- ==================== follows 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'follows' AND index_name = 'idx_follows_follower' LIMIT 1) THEN
        CREATE INDEX idx_follows_follower ON follows (follower_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'follows' AND index_name = 'idx_follows_followed' LIMIT 1) THEN
        CREATE INDEX idx_follows_followed ON follows (followed_id);
    END IF;

    -- ==================== post_tags 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'post_tags' AND index_name = 'idx_post_tags_post_id' LIMIT 1) THEN
        CREATE INDEX idx_post_tags_post_id ON post_tags (post_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'post_tags' AND index_name = 'idx_post_tags_tag_id' LIMIT 1) THEN
        CREATE INDEX idx_post_tags_tag_id ON post_tags (tag_id);
    END IF;

    -- ==================== posts 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'posts' AND index_name = 'idx_posts_status_hot' LIMIT 1) THEN
        CREATE INDEX idx_posts_status_hot ON posts (status, view_count DESC, like_count DESC, created_at DESC);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'posts' AND index_name = 'idx_posts_user_id' LIMIT 1) THEN
        CREATE INDEX idx_posts_user_id ON posts (user_id);
    END IF;

    -- ==================== post_views 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'post_views' AND index_name = 'idx_post_views_user' LIMIT 1) THEN
        CREATE INDEX idx_post_views_user ON post_views (user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'post_views' AND index_name = 'idx_post_views_user_id_desc' LIMIT 1) THEN
        CREATE INDEX idx_post_views_user_id_desc ON post_views (user_id, id DESC);
    END IF;

    -- ==================== comments 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'comments' AND index_name = 'idx_comments_user_id' LIMIT 1) THEN
        CREATE INDEX idx_comments_user_id ON comments (user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'comments' AND index_name = 'idx_comments_post_id' LIMIT 1) THEN
        CREATE INDEX idx_comments_post_id ON comments (post_id);
    END IF;

    -- ==================== user_tag_weights 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'user_tag_weights' AND index_name = 'idx_user_tag_weights_user' LIMIT 1) THEN
        CREATE INDEX idx_user_tag_weights_user ON user_tag_weights (user_id);
    END IF;

    -- ==================== app_configs 表 ====================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'app_configs' AND index_name = 'idx_app_configs_key' LIMIT 1) THEN
        CREATE INDEX idx_app_configs_key ON app_configs (config_key);
    END IF;
END$$

DELIMITER ;

CALL create_idx();

DROP PROCEDURE IF EXISTS create_idx;
