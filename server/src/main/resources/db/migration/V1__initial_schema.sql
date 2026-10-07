-- Fresh MySQL 8 schema. No user data is embedded. Existing databases require explicit adoption.
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider` enum('APPLE','GOOGLE','KAKAO') NOT NULL,
  `provider_id` varchar(255) NOT NULL,
  `user_pk` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_account_provider_user` (`provider`,`provider_id`),
  KEY `FK73i0wjbjqlnl414mq98f8cikd` (`user_pk`),
  CONSTRAINT `FK73i0wjbjqlnl414mq98f8cikd` FOREIGN KEY (`user_pk`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_news` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `inspection_status` enum('APPROVED','DENIED','PENDING') DEFAULT NULL,
  `publication_date` date NOT NULL,
  `source_published_at` datetime(6) DEFAULT NULL,
  `source_title` varchar(500) DEFAULT NULL,
  `source_url` varchar(2048) DEFAULT NULL,
  `summary` varchar(5000) DEFAULT NULL,
  `title_image_credit` varchar(500) DEFAULT NULL,
  `title_image_origin` varchar(20) DEFAULT NULL,
  `title_image_source_url` varchar(2048) DEFAULT NULL,
  `title_img_url` varchar(2048) DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `content_type` enum('AI_THEORY','NEWS') NOT NULL,
  `first_used_at` datetime(6) DEFAULT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `publication_status` enum('PUBLISHED','READY') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_news_selection` (`inspection_status`,`content_type`,`publication_status`,`approved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_news_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `card_news_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7wcbmqfu4vhfbonusu74x3i5g` (`card_news_id`),
  KEY `FK3n0u33d37jqp81oslsaddia56` (`category_id`),
  CONSTRAINT `FK3n0u33d37jqp81oslsaddia56` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`),
  CONSTRAINT `FK7wcbmqfu4vhfbonusu74x3i5g` FOREIGN KEY (`card_news_id`) REFERENCES `card_news` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_news_key_points` (
  `card_news_id` bigint NOT NULL,
  `key_points` varchar(1000) NOT NULL,
  KEY `FKnde8som00p4elp7008dnlfq71` (`card_news_id`),
  CONSTRAINT `FKnde8som00p4elp7008dnlfq71` FOREIGN KEY (`card_news_id`) REFERENCES `card_news` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_slide` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(5000) NOT NULL,
  `img_url` varchar(2048) DEFAULT NULL,
  `sequence` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `card_news_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `image_credit` varchar(500) DEFAULT NULL,
  `image_origin` varchar(20) DEFAULT NULL,
  `image_source_url` varchar(2048) DEFAULT NULL,
  `layout` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK86iso11mx9i4ostw112n1e7yt` (`card_news_id`),
  CONSTRAINT `FK86iso11mx9i4ostw112n1e7yt` FOREIGN KEY (`card_news_id`) REFERENCES `card_news` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `daily_news_edition` (
  `delivery_date` date NOT NULL,
  `approval_cutoff` datetime(6) NOT NULL,
  `card_news_id` bigint DEFAULT NULL,
  `selected_at` datetime(6) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`delivery_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `platform` enum('ANDROID','IOS') NOT NULL,
  `token` varchar(512) NOT NULL,
  `user_id` bigint NOT NULL,
  `installation_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoaccue9kxei35rbe5thnv18ye` (`token`),
  UNIQUE KEY `uk_device_installation` (`platform`,`installation_id`),
  KEY `idx_device_user_active` (`user_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `card_news_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_user_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK72xb8hbvrkr5mw7hpe4cjlb0r` (`card_news_id`),
  KEY `FKhqwsmofhd6aremft1usbyyaw0` (`user_user_id`),
  CONSTRAINT `FK72xb8hbvrkr5mw7hpe4cjlb0r` FOREIGN KEY (`card_news_id`) REFERENCES `card_news` (`id`),
  CONSTRAINT `FKhqwsmofhd6aremft1usbyyaw0` FOREIGN KEY (`user_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempts` int NOT NULL,
  `card_news_id` bigint NOT NULL,
  `delivery_date` date NOT NULL,
  `device_token_id` bigint NOT NULL,
  `last_error` varchar(100) DEFAULT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','FAILED','PENDING','PROCESSING','SENT') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_news_delivery_day` (`user_id`,`device_token_id`,`delivery_date`),
  KEY `idx_news_delivery_due` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news_generation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_key` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `request_json` mediumtext NOT NULL,
  `response_json` mediumtext,
  `source_url` varchar(2048) NOT NULL,
  `task` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_generation_attempt` (`attempt_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `card_news_id` bigint DEFAULT NULL,
  `payload` mediumtext NOT NULL,
  `payload_hash` varchar(64) NOT NULL,
  `review_note` varchar(2000) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `source_hash` varchar(64) NOT NULL,
  `source_url` varchar(2048) NOT NULL,
  `status` enum('APPROVED','DENIED','PENDING') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_source` (`source_hash`),
  KEY `idx_submission_status` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `content` text NOT NULL,
  `published` bit(1) NOT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notice_published` (`published`,`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_allowed` bit(1) NOT NULL,
  `setting_time` time NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKprsli08qedapfuoqx92jd8o7x` (`user_id`),
  KEY `idx_notification_due` (`is_allowed`,`setting_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `profile_img` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `img_url` varchar(2048) NOT NULL,
  `name` varchar(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_onboarding_completed` bit(1) NOT NULL,
  `last_logined_at` datetime(6) DEFAULT NULL,
  `nickname` varchar(255) NOT NULL,
  `user_id` varchar(255) NOT NULL,
  `user_role` enum('ADMIN','USER') DEFAULT NULL,
  `profile_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6efs5vmce86ymf5q7lmvn2uuf` (`user_id`),
  KEY `FK7m4n22x9kqwgscs8em0df8l2h` (`profile_id`),
  CONSTRAINT `FK7m4n22x9kqwgscs8em0df8l2h` FOREIGN KEY (`profile_id`) REFERENCES `profile_img` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- Autogenerated: do not edit this file

CREATE TABLE BATCH_JOB_INSTANCE (
	JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY,
	VERSION BIGINT,
	JOB_NAME VARCHAR(100) NOT NULL,
	JOB_KEY VARCHAR(32) NOT NULL,
	constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION (
	JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY,
	VERSION BIGINT,
	JOB_INSTANCE_ID BIGINT NOT NULL,
	CREATE_TIME DATETIME(6) NOT NULL,
	START_TIME DATETIME(6) DEFAULT NULL,
	END_TIME DATETIME(6) DEFAULT NULL,
	STATUS VARCHAR(10),
	EXIT_CODE VARCHAR(2500),
	EXIT_MESSAGE VARCHAR(2500),
	LAST_UPDATED DATETIME(6),
	constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
	references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
	JOB_EXECUTION_ID BIGINT NOT NULL,
	PARAMETER_NAME VARCHAR(100) NOT NULL,
	PARAMETER_TYPE VARCHAR(100) NOT NULL,
	PARAMETER_VALUE VARCHAR(2500),
	IDENTIFYING CHAR(1) NOT NULL,
	constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION (
	STEP_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY,
	VERSION BIGINT NOT NULL,
	STEP_NAME VARCHAR(100) NOT NULL,
	JOB_EXECUTION_ID BIGINT NOT NULL,
	CREATE_TIME DATETIME(6) NOT NULL,
	START_TIME DATETIME(6) DEFAULT NULL,
	END_TIME DATETIME(6) DEFAULT NULL,
	STATUS VARCHAR(10),
	COMMIT_COUNT BIGINT,
	READ_COUNT BIGINT,
	FILTER_COUNT BIGINT,
	WRITE_COUNT BIGINT,
	READ_SKIP_COUNT BIGINT,
	WRITE_SKIP_COUNT BIGINT,
	PROCESS_SKIP_COUNT BIGINT,
	ROLLBACK_COUNT BIGINT,
	EXIT_CODE VARCHAR(2500),
	EXIT_MESSAGE VARCHAR(2500),
	LAST_UPDATED DATETIME(6),
	constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
	STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT,
	constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
	references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
	JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT,
	constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION_SEQ (
	ID BIGINT NOT NULL,
	UNIQUE_KEY CHAR(1) NOT NULL,
	constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_STEP_EXECUTION_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_STEP_EXECUTION_SEQ);

CREATE TABLE BATCH_JOB_EXECUTION_SEQ (
	ID BIGINT NOT NULL,
	UNIQUE_KEY CHAR(1) NOT NULL,
	constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_EXECUTION_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_JOB_EXECUTION_SEQ);

CREATE TABLE BATCH_JOB_INSTANCE_SEQ (
	ID BIGINT NOT NULL,
	UNIQUE_KEY CHAR(1) NOT NULL,
	constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_INSTANCE_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_JOB_INSTANCE_SEQ);
