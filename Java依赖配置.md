# Java Spring Boot 项目依赖配置

根据接口文档的需求，以下是项目所需的主要依赖配置：

## Maven 依赖配置 (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot 核心依赖 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- 数据库相关 -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>8.0.33</version>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.5.3.1</version>
    </dependency>
    
    <!-- 认证相关 -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- API 文档 -->
    <dependency>
        <groupId>io.springfox</groupId>
        <artifactId>springfox-boot-starter</artifactId>
        <version>3.0.0</version>
    </dependency>
    
    <!-- HTTP 客户端 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- 工具库 -->
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>31.1-jre</version>
    </dependency>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
        <version>3.12.0</version>
    </dependency>
    
    <!-- 邮件发送 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
    
    <!-- Redis 缓存 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- 定时任务 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-quartz</artifactId>
    </dependency>
    
    <!-- 数据校验 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- 日志 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
    </dependency>
</dependencies>
```

## Gradle 依赖配置 (build.gradle)

```groovy
dependencies {
    // Spring Boot 核心依赖
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    
    // 数据库相关
    implementation 'mysql:mysql-connector-java:8.0.33'
    implementation 'com.baomidou:mybatis-plus-boot-starter:3.5.3.1'
    
    // 认证相关
    implementation 'io.jsonwebtoken:jjwt:0.11.5'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    
    // API 文档
    implementation 'io.springfox:springfox-boot-starter:3.0.0'
    
    // HTTP 客户端
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    
    // 工具库
    implementation 'com.google.guava:guava:31.1-jre'
    implementation 'org.apache.commons:commons-lang3:3.12.0'
    
    // 邮件发送
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    
    // Redis 缓存
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    
    // 定时任务
    implementation 'org.springframework.boot:spring-boot-starter-quartz'
    
    // 数据校验
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    
    // 日志
    implementation 'org.slf4j:slf4j-api'
    implementation 'ch.qos.logback:logback-classic'
}
```

## 依赖说明

### 核心依赖
- **spring-boot-starter-web**: 提供Web服务支持
- **spring-boot-starter-test**: 提供测试支持

### 数据库相关
- **mysql-connector-java**: MySQL数据库驱动
- **mybatis-plus-boot-starter**: MyBatis-Plus增强框架，简化数据库操作

### 认证相关
- **jjwt**: JWT令牌生成和验证
- **spring-boot-starter-security**: Spring Security安全框架

### API文档
- **springfox-boot-starter**: Swagger API文档生成

### HTTP客户端
- **spring-boot-starter-webflux**: 提供WebClient用于与Python服务交互

### 工具库
- **guava**: Google工具库，提供各种实用工具
- **commons-lang3**: Apache Commons工具库

### 邮件发送
- **spring-boot-starter-mail**: 提供邮件发送功能，用于通知用户评估结果

### 缓存
- **spring-boot-starter-data-redis**: Redis缓存支持，用于存储临时数据

### 定时任务
- **spring-boot-starter-quartz**: 定时任务框架，用于执行定时任务如逾期状态更新

### 数据校验
- **spring-boot-starter-validation**: 提供数据校验功能

### 日志
- **slf4j-api**: 日志接口
- **logback-classic**: 日志实现

## 配置说明

### 数据库配置 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/financial_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: org.example.financialsystem.model
  global-config:
    db-config:
      id-type: auto
```

### Redis配置 (application.yml)

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
    database: 0
```

### Swagger配置

```java
@Configuration
@EnableSwagger2
public class SwaggerConfig {
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.example.financialsystem.controller"))
                .paths(PathSelectors.any())
                .build()
                .apiInfo(apiInfo());
    }
    
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("金融系统API")
                .description("金融系统接口文档")
                .version("1.0.0")
                .build();
    }
}
```

### JWT配置

```java
@Configuration
public class JwtConfig {
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expire}")
    private long expire;
    
    public String generateToken(String userId) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire * 1000);
        
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }
    
    public Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }
}
```

### 安全配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
            .antMatchers("/auth/**", "/admin/register", "/admin/login").permitAll()
            .anyRequest().authenticated()
            .and()
            .addFilterBefore(new JwtFilter(), UsernamePasswordAuthenticationFilter.class);
    }
}
```

## 注意事项

1. **版本兼容性**：确保各依赖版本与Spring Boot版本兼容
2. **生产环境**：生产环境中应移除Swagger等开发工具依赖
3. **安全配置**：生产环境中应加强安全配置，如设置复杂的JWT密钥
4. **性能优化**：根据实际业务需求，可适当调整依赖配置
5. **监控**：可考虑添加Spring Boot Actuator等监控工具

以上依赖配置基本满足接口文档中描述的所有功能需求，可根据实际项目情况进行调整。