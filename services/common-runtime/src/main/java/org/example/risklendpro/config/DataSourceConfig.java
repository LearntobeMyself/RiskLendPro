package org.example.risklendpro.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean(name = "primaryDataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "creditDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.credit")
    public DataSource creditDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "primarySqlSessionFactory")
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(@Qualifier("primaryDataSource") DataSource dataSource,
                                                      MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        return sqlSessionFactory(dataSource, mybatisPlusInterceptor,
                "org.example.risklendpro.user.entity,org.example.risklendpro.loan.entity,org.example.risklendpro.risk.entity");
    }

    @Bean(name = "creditSqlSessionFactory")
    public SqlSessionFactory creditSqlSessionFactory(@Qualifier("creditDataSource") DataSource dataSource,
                                                     MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        return sqlSessionFactory(dataSource, mybatisPlusInterceptor, "org.example.risklendpro.risk.credit");
    }

    /**
     * 工厂是手写的，不会走 MyBatis-Plus 自动配置。不把拦截器挂上的话，
     * 带 @Version 的更新 SQL 仍会引用 MP_OPTLOCK_VERSION_ORIGINAL，但运行时没人写入这个参数。
     */
    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                                       MybatisPlusInterceptor mybatisPlusInterceptor,
                                                       String typeAliasesPackage) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setTypeAliasesPackage(typeAliasesPackage);
        bean.setPlugins(mybatisPlusInterceptor);
        return bean.getObject();
    }

    @Bean(name = "primaryTransactionManager")
    @Primary
    public DataSourceTransactionManager primaryTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "creditTransactionManager")
    public DataSourceTransactionManager creditTransactionManager(@Qualifier("creditDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}