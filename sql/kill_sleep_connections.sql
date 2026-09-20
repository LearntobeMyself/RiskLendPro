-- =============================================================
-- RiskLendPro 云端 MySQL 连接数爆满应急清理脚本
-- 服务器：47.109.109.231:3306
-- 用法：在【服务器上】执行（本地直连会被 1040 拒绝）
--   mysql -u root -p < /path/to/kill_sleep_connections.sql
--  或： mysql -u root -p  然后粘贴下面的语句
-- =============================================================

-- 1) 先看现状：总连接数、上限、各来源占用
SELECT @@max_connections                              AS 上限,
       (SELECT COUNT(*) FROM information_schema.PROCESSLIST) AS 当前连接数,
       (SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE COMMAND='Sleep') AS 空闲占用;

SELECT USER,
       SUBSTRING_INDEX(HOST, ':', 1)        AS 来源IP,
       COUNT(*)                             AS 总连接,
       SUM(COMMAND = 'Sleep')               AS 空闲连接,
       MAX(TIME)                            AS 最长空闲秒
FROM information_schema.PROCESSLIST
GROUP BY USER, 来源IP
ORDER BY 总连接 DESC;

-- 2) 【先干这一步，通常就够了】临时把上限调大，先恢复服务可用性
--    注意：这是运行时生效，MySQL 重启后会回到配置文件的值
SET GLOBAL max_connections = 1000;
SET GLOBAL wait_timeout = 300;      -- 空闲 5 分钟就回收（默认 28800 秒 = 8 小时，太长）
SET GLOBAL interactive_timeout = 300;

-- 3) 生成批量 KILL 语句：杀掉所有空闲超过 120 秒的连接
--    （先 SELECT 出来看一眼，确认没有误伤正在跑的长事务）
SELECT CONCAT('KILL ', ID, ';') AS kill_sql
FROM information_schema.PROCESSLIST
WHERE COMMAND = 'Sleep'
  AND TIME > 120
  AND USER <> 'system user';

-- 4) 确认无误后，用下面这条一次性生成并执行（MySQL 8 可用，粘贴到 mysql 客户端执行）：
--    SELECT GROUP_CONCAT(CONCAT('KILL ', ID, ';') SEPARATOR ' ')
--    INTO @sql
--    FROM information_schema.PROCESSLIST
--    WHERE COMMAND='Sleep' AND TIME > 120 AND USER <> 'system user';
--    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 5) 清理后复查
SELECT COUNT(*)                                        AS 清理后连接数,
       SUM(COMMAND = 'Sleep')                          AS 剩余空闲,
       @@max_connections                               AS 当前上限
FROM information_schema.PROCESSLIST;
