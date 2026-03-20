package com.carmarket.carmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class CarmarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(CarmarketApplication.class, args);
	}

}
