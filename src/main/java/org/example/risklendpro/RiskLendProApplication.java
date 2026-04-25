package org.example.risklendpro;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@MapperScan("org.example.risklendpro.mapper")// 扫描 mapper 包
@EnableTransactionManagement
public class RiskLendProApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskLendProApplication.class, args);
    }

}
