package com.eventPro.eventPro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // indique a spring active le systeme de scheduling
@SpringBootApplication
public class EventProApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventProApplication.class, args);
	}

}
