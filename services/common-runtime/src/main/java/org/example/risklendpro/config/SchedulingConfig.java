package org.example.risklendpro.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

import javax.sql.DataSource;

/**
 * 全局定时任务开关与分布式调度锁（ShedLock）。
 * 锁表位于主库 PRIMARY，防止多实例重复执行同一 @Scheduled 任务。
 * 需先执行 sql/shedlock_table.sql 创建 shedlock 表。
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M", defaultLockAtLeastFor = "PT1S")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}