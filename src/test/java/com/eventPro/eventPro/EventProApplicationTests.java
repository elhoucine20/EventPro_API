package com.eventPro.eventPro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // pour utuluser le file application-test.properties  by default derictement application.properties
class EventProApplicationTests {

	@Test
	void contextLoads() {
	}
}
