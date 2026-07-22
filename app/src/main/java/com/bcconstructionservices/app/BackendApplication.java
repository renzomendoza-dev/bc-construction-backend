package com.bcconstructionservices.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// scanBasePackages ensures every module (inventory, sales, ecommerce, user, ...)
// gets picked up automatically as long as it lives under com.bcconstructionservices
@SpringBootApplication(scanBasePackages = "com.bcconstructionservices")
@EnableJpaRepositories(basePackages = "com.bcconstructionservices")
@EntityScan(basePackages = "com.bcconstructionservices")
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
