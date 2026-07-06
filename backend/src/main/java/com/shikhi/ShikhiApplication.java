package com.shikhi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // guest-account reaper (GuestReaper) and any future scheduled jobs
public class ShikhiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShikhiApplication.class, args);
	}

}
