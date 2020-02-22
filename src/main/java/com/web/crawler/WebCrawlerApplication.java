package com.web.crawler;

import com.web.crawler.domain.WebCrawlerOrchestrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebCrawlerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebCrawlerApplication.class, args);
		new WebCrawlerOrchestrator().webCrawler("jesus");
	}

}
