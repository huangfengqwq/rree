1.DemoApplication.java:package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
2.Chatcontroller:package controller;

import Service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * 处理前端发送的 POST 请求
     *
     * @param request 包含用户消息的 JSON 对象（如 { "message": "你好" }）
     * @return 返回包含机器人回复的 JSON 对象（如 { "reply": "这是机器人的回复: 你好" }）
     */
    @PostMapping
    public Map<String, String> getBotResponse(@RequestBody Map<String, String> request) {
        // 获取前端传递的消息
        String userMessage = request.get("message");

        // 调用服务层逻辑处理消息并获取机器人回复
        String botReply = chatService.getBotResponse(userMessage);

        // 构造返回的 JSON 响应
        Map<String, String> response = new HashMap<>();
        response.put("reply", botReply);
        return response;
    }
3.ChatMessage:
  package model;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userMessage;
    private String botReply;
}
4.ChatMessageRepository:package repository;
import model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
}
5.ChatService:
package Service;

import model.ChatMessage;
import repository.ChatMessageRepository;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository chatMessageRepository;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * 获取 AI 的回复
     */
    public String getBotResponse(String userMessage) {
        // 检查是否有自定义回答
        String customResponse = getCustomResponse(userMessage);
        if (customResponse != null) {
            // 保存聊天记录
            saveChatMessage(userMessage, customResponse);
            return customResponse;
        }

        try {
            // 构造请求体
            String jsonBody = "{\"prompt\":\"" + userMessage + "\",\"max_tokens\":50}";
            RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

            // 构造 HTTP 请求
            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/engines/text-davinci-003/completions")
                    .addHeader("Authorization", "Bearer " + openaiApiKey)
                    .post(body)
                    .build();

            // 发送请求并获取响应
            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logErrorResponse(response);
                    return "喵喵喵，请求错误了，请看看网络然后重试。";
                }

                String responseBody = null;
                if (response.body() != null) {
                    responseBody = response.body().string();
                }
                String botReply = parseOpenAIResponse(responseBody);

                // 保存聊天记录
                saveChatMessage(userMessage, botReply);

                return botReply;
            }
        } catch (Exception e) {
            logger.error("获取回复时出错了", e);
            return "请求失败了qwq。";
        }
    }

    /**
     * 解析 OpenAI 的响应
     */
    private String parseOpenAIResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("text").asText().trim();
            }
        } catch (Exception e) {
            logger.error("解析 OpenAI 响应失败了,qwq: {}", responseBody, e);
        }
        return "解析回复失败qwq。";
    }

    /**
     * 保存聊天记录
     */
    private void saveChatMessage(String userMessage, String botReply) {
        try {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setUserMessage(userMessage);
            chatMessage.setBotReply(botReply);
            chatMessageRepository.save(chatMessage);
        } catch (Exception e) {
            logger.error("保存聊天消息失败qwq", e);
        }
    }

    /**
     * 记录错误响应
     */
    private void logErrorResponse(okhttp3.Response response) {
        try {
            if (response.body() != null) {
                logger.error("OpenAI API 错误: code={}, message={}", response.code(), response.body().string());
            }
        } catch (Exception e) {
            logger.error("记录错误响应失败,qwq", e);
        }
    }

    /**
     * 自定义回答逻辑
     */
    /**
     * 自定义回答逻辑（基于关键词匹配）
     */
    private String getCustomResponse(String userMessage) {
        // 将用户输入转换为小写，方便进行不区分大小写的匹配
        String lowerCaseMessage = userMessage.toLowerCase();
        if ("你好".equalsIgnoreCase(userMessage)||"您好".equalsIgnoreCase(lowerCaseMessage)) {
            return "你好！我是黄风聊天机器人1.0,有什么可以帮您的吗？";
        }

        // 定义关键词和对应的回答
        if (lowerCaseMessage.contains("再见") || lowerCaseMessage.contains("拜拜")) {
            return "再见！祝您有个美好的一天 qwq!！";
        } else if (lowerCaseMessage.contains("你是谁") || lowerCaseMessage.contains("你的名字")) {
            return "我是黄风聊天机器人1.0，随时为您提供帮助！";
        } else if (lowerCaseMessage.contains("天气")) {
            return "抱歉，我无法查询实时天气。您可以尝试使用其他工具哦qwq~";
        } else if (lowerCaseMessage.contains("时间") || lowerCaseMessage.contains("几点")) {
            return "当前时间是：" + getCurrentTime();
        }
        else if (lowerCaseMessage.contains("作者名字") || lowerCaseMessage.contains("作者"))
            return "作者是软件工程的一名大一新生,网名黄风,这个只是一个" +
     "练习项目,希望大家用的愉快,以后等我学习更多可能会更新更多内容,现在单纯是套壳ai,不喜请喷轻点qwq... 感谢您的使用！";
     else if   (lowerCaseMessage.contains("黄风"))return "黄风是我好多年的游戏名字了,所以叫做黄风";
        // 如果没有匹配的规则，返回 null
        return null;
    }

    /**
     * 获取当前时间的字符串表示
     */
    private String getCurrentTime() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
6.WebConfig:
package WebConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ReactConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允许所有路径
                .allowedOrigins("http://localhost:3000") // 允许来自前端的请求
                .allowedMethods("GET", "POST", "PUT", "DELETE") // 允许的 HTTP 方法
                .allowedHeaders("*"); // 允许所有头信息
    }
}
7.application.properties
spring.datasource.url=jdbc:mysql://obmt6mcyp24qwf0g-mi.aliyun-cn-hangzhou-internet.oceanbase.cloud:3306/huangfeng?useSSL=false
spring.datasource.username=rree1
spring.datasource.password=KUAIle1314..
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
openai.api.key=sk-proj-y2fIJD_?g-4YDY7tIVZ7TqnGUjWO5tKpf62pb3DVoZTtHHJ_VQ9tk6_uuP1YbdJMIydkbJ6xsMT3BlbkFJ57j0O4fGUjyI8ZPwzrNxDtkxKIURjoxJINg52iP04eZnnwl3hEC_07RG8x_NqxqiLyLl2M7J4A
8.pom.xml:<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.5.0-SNAPSHOT</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>


	<groupId>com.example</groupId>
	<artifactId>demo</artifactId>
	<version>1.0-SNAPSHOT</version>
	<name>demo</name>
	<description>Demo project for Spring Boot</description>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>17</java.version>
	</properties>
	<dependencies>
		<!-- Spring Boot Starter Web -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>


		<!-- Spring Boot Starter Data JPA -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>

		<!-- MySQL Connector -->
		<dependency>
			<groupId>com.mysql</groupId>
			<artifactId>mysql-connector-j</artifactId>
			<scope>runtime</scope>
		</dependency>

		<!-- OkHttp for HTTP Requests -->
		<dependency>
			<groupId>com.squareup.okhttp3</groupId>
			<artifactId>okhttp</artifactId>
			<version>4.9.3</version> <!-- 使用最新稳定版本 -->
		</dependency>

		<!-- Jackson for JSON Handling -->
		<dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-databind</artifactId>
			<version>2.18.3</version> <!-- 使用最新稳定版本 -->
		</dependency>

		<!-- Lombok for Simplifying Code -->
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<version>1.18.28</version> <!-- 使用最新稳定版本 -->
			<optional>true</optional>
		</dependency>

		<!-- Spring Boot DevTools for Development -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-devtools</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>

		<!-- Logging Dependencies -->
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-api</artifactId>
		</dependency>
		<dependency>
			<groupId>ch.qos.logback</groupId>
			<artifactId>logback-classic</artifactId>
		</dependency>

		<!-- Testing Dependencies -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>junit</groupId>
			<artifactId>junit</artifactId>
			<version>3.8.1</version>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>mysql</groupId>
			<artifactId>mysql-connector-java</artifactId>
			<version>8.0.33</version> <!-- 确保这是最新版本 -->
		</dependency>
	</dependencies>


	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<annotationProcessorPaths>
						<path>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</path>
					</annotationProcessorPaths>
				</configuration>
			</plugin>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
				<configuration>
					<excludes>
						<exclude>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</exclude>
					</excludes>
				</configuration>
			</plugin>
		</plugins>
	</build>
	<repositories>
		<repository>
			<id>spring-milestones</id>
			<name>Spring Milestones</name>
			<url>https://repo.spring.io/milestone</url>
			<snapshots>
				<enabled>false</enabled>
			</snapshots>
		</repository>
		<repository>
			<id>spring-snapshots</id>
			<name>Spring Snapshots</name>
			<url>https://repo.spring.io/snapshot</url>
			<releases>
				<enabled>false</enabled>
			</releases>
		</repository>
	</repositories>
	<pluginRepositories>
		<pluginRepository>
			<id>spring-milestones</id>
			<name>Spring Milestones</name>
			<url>https://repo.spring.io/milestone</url>
			<snapshots>
				<enabled>false</enabled>
			</snapshots>
		</pluginRepository>
		<pluginRepository>
			<id>spring-snapshots</id>
			<name>Spring Snapshots</name>
			<url>https://repo.spring.io/snapshot</url>
			<releases>
				<enabled>false</enabled>
			</releases>
		</pluginRepository>
	</pluginRepositories>

</project>

  




