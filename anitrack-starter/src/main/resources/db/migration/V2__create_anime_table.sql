CREATE TABLE t_anime (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    bangumi_id BIGINT NOT NULL COMMENT 'Bangumi外部条目ID',
    title_cn VARCHAR(255) DEFAULT NULL COMMENT '中文名，对应Bangumi name_cn',
    title_original VARCHAR(255) NOT NULL COMMENT '原名，对应Bangumi name',
    cover_url VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',
    total_episodes INT DEFAULT NULL COMMENT '总集数，取Bangumi的eps字段，0或NULL表示未知',
    air_date DATE DEFAULT NULL COMMENT '放送日期，Bangumi该字段非必填',
    summary TEXT COMMENT '简介',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bangumi_id (bangumi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='番剧目录本地缓存表';
