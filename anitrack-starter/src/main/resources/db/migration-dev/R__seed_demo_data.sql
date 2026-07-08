-- 演示种子数据：2 用户 + 4 番剧 + 5 追番（覆盖全部状态）+ 3 评价
-- 密码均为明文 111111 的 BCrypt 哈希
-- 使用 ON DUPLICATE KEY UPDATE 实现幂等，修改本文件后重启会覆盖更新

INSERT INTO t_user (id, username, password_hash, nickname, role) VALUES
    (1, 'alice', '$2a$10$lHTFe5DwbDX4.iE6l.2TseSg1hrxpzWTP/6mfGiJUNZb80rFg37Yq', '爱丽丝', 'USER'),
    (2, 'bob', '$2a$10$lHTFe5DwbDX4.iE6l.2TseSg1hrxpzWTP/6mfGiJUNZb80rFg37Yq', '鲍勃', 'USER')
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    password_hash = VALUES(password_hash),
    nickname = VALUES(nickname),
    role = VALUES(role);

INSERT INTO t_anime (id, bangumi_id, title_cn, title_original, cover_url, total_episodes, air_date, summary) VALUES
    (1, 400602, '葬送的芙莉莲', '葬送のフリーレン', 'https://lain.bgm.tv/r/400/pic/cover/l/13/c5/400602_ZI8Y9.jpg', 36, '2023-09-29', '魔法使芙莉莲和勇者辛美尔等人一起，历经十年的冒险之后击败了魔王，为世界带来了和平。'),
    (2, 245665, '鬼灭之刃', '鬼滅の刃', 'https://lain.bgm.tv/r/400/pic/cover/l/9d/d1/245665_5an54.jpg', 26, '2019-04-06', '大正时期，卖炭少年炭治郎的家人被鬼杀死，妹妹祢豆子变成了鬼。'),
    (3, 55770, '进击的巨人', '進撃の巨人', 'https://lain.bgm.tv/r/400/pic/cover/l/78/c9/55770_HsJfh.jpg', 26, '2013-04-06', '人类被巨人捕食而崩溃，幸存者建造墙壁防止巨人入侵。'),
    (4, 329906, '间谍过家家', 'SPY×FAMILY', 'https://lain.bgm.tv/r/400/pic/cover/l/de/4a/329906_hmtVD.jpg', 12, '2022-04-09', '东西国冷战时代，间谍黄昏组建虚假家庭执行任务。')
ON DUPLICATE KEY UPDATE
    title_cn = VALUES(title_cn),
    title_original = VALUES(title_original),
    cover_url = VALUES(cover_url),
    total_episodes = VALUES(total_episodes),
    air_date = VALUES(air_date),
    summary = VALUES(summary);

INSERT INTO t_watchlist_item (user_id, anime_id, status, current_episode) VALUES
    (1, 1, 'WATCHING', 12),
    (1, 2, 'WATCHED', 26),
    (1, 3, 'WANT_TO_WATCH', 0),
    (2, 1, 'WATCHING', 5),
    (2, 4, 'DROPPED', 3)
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    current_episode = VALUES(current_episode);

INSERT INTO t_review (user_id, anime_id, score, content) VALUES
    (1, 2, 9, '作画与剧情都在线，炭治郎一家的悲剧开篇很有冲击力。'),
    (2, 1, 10, '芙莉莲对时间与生命的刻画非常细腻，年度最佳。'),
    (1, 3, 8, '设定惊艳，后期略有烂尾但整体仍是神作。')
ON DUPLICATE KEY UPDATE
    score = VALUES(score),
    content = VALUES(content);
