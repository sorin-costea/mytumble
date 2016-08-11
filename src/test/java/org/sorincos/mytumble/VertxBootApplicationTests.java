package org.sorincos.mytumble;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = VertxBootApplication.class)
@ConfigurationProperties(prefix = "connection")
public class VertxBootApplicationTests {

	private String key;
	private String databaseName;

	@Test
	public void contextLoads() {
		Assert.assertNotNull(key);
		Assert.assertNotNull(databaseName);
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

}
