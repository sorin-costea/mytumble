package org.sorincos.mytumble;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = VertxBootApplication.class)
@ConfigurationProperties(prefix = "database")
public class VertxBootApplicationTests {

	private String name;

	@Test
	public void contextLoads() {
		Assert.assertNotNull(name);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
