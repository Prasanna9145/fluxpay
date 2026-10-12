package com.prasanna.fluxpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FluxpayApplication {

	public static void main(String[] args) {
		SpringApplication.run(FluxpayApplication.class, args);
	}

}
