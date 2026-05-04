package org.example.risklendpro.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "org.example.risklendpro.mapper.credit", 
           sqlSessionFactoryRef = "creditSqlSessionFactory")
public class CreditMapperScanConfig {
}