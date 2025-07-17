package com.synapsehealth.doc_uploader_test_client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point that delegates to the MainRunner for command line processing.
 * This allows the application to be run in both standard jar and Spring Boot modes.
 */
@SpringBootApplication
public class TestClientApplication {

	public static void main(String[] args) {
		// Initialize Spring context
		var context = SpringApplication.run(TestClientApplication.class, args);

		// Delegate command line processing to MainRunner
        MainRunner.main(args);
	}
}
