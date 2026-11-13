package br.com.campusapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampusApiApplication.class, args);
	}

}
