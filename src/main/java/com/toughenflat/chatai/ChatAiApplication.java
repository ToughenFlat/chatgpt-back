package com.toughenflat.chatai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@MapperScan("com.toughenflat.chatai.mapper")
public class ChatAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatAiApplication.class, args);
    }

}
