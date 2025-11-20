package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootApplication
public class Application {

	@Autowired
	private MonitoringConfigService configService;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(Application.class);
		
		// Create a temporary context to read arguments
		ApplicationArguments arguments = new DefaultApplicationArguments(args);
		
		if (arguments.containsOption("no-web")) {
			app.setWebApplicationType(WebApplicationType.NONE);
		}
		
		app.run(args);
	}

	@Bean
	public CommandLineRunner commandLineRunner() {
		return args -> {
			// Load monitoring rules from configuration file
			// You can specify config path via system property: -Dmonitoring.config=path/to/config.json
			String configPath = System.getProperty("monitoring.config", 
				"src/main/resources/monitoring-rules.json");
			configService.loadConfiguration(configPath);
		};
	}
}