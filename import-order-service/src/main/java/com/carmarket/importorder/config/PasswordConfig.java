package com.carmarket.importorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder(ImportOrderProperties properties) {
        return new BCryptPasswordEncoder(properties.password().bcryptCost());
    }
}
