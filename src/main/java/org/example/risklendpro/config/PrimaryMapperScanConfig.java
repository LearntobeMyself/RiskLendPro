package org.example.risklendpro.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
    basePackages = {
        "org.example.risklendpro.user.mapper",
        "org.example.risklendpro.loan.mapper",
        "org.example.risklendpro.risk.mapper"
    },
    sqlSessionFactoryRef = "primarySqlSessionFactory"
)
public class PrimaryMapperScanConfig {
}