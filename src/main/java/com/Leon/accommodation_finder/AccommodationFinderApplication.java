package com.Leon.accommodation_finder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class AccommodationFinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccommodationFinderApplication.class, args);
	}

}
