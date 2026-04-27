-- SQL to create appraisal_comments table
CREATE TABLE IF NOT EXISTS `appraisal_comments` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `content` TEXT NOT NULL,
  `appraisal_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `parent_id` BIGINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX (`appraisal_id`),
  INDEX (`user_id`),
  INDEX (`parent_id`),
  CONSTRAINT `fk_appraisal_comments_appraisal` FOREIGN KEY (`appraisal_id`) REFERENCES `appraisals` (`id`) ON DELETE CASCADE
);
