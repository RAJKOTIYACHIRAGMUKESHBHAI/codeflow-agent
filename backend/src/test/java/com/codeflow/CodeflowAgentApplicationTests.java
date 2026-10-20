
package com.codeflow;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CodeflowAgentApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointReturnsUpStatus() {
		HealthController controller = new HealthController();

		Map<String, String> response = controller.health();

		assertEquals("UP", response.get("status"));
		assertEquals("CodeFlow Agent", response.get("application"));
	}
}
