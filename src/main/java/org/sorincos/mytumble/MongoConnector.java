package org.sorincos.mytumble;

import java.util.LinkedList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

@Component
@ConfigurationProperties(prefix = "database")
public class MongoConnector extends AbstractVerticle {
	private String name;

	private MongoClient mongo;

	@Override
	public void start() throws Exception {
		// Create a mongo client using all defaults (connect to localhost and
		// default port) using the given name
		mongo = MongoClient.createShared(vertx, new JsonObject().put("db_name", name));

		// the load function just populates some data on the storage
		loadData(mongo);

		// and listen for messages
		// TODO can be blocking?
		vertx.eventBus().consumer("mytumble.mongo.findall", message -> {
			mongo.find("users", new JsonObject(), lookup -> {
			  final JsonArray json = new JsonArray();

			  for (JsonObject o : lookup.result()) {
				  json.add(o);
			  }
			  message.reply(json);
		  });
		});

	}

	public String getName() {
		return name;
	}

	public void setName(String database) {
		this.name = database;
	}

	private void loadData(MongoClient db) {
		db.dropCollection("users", drop -> {
			if (drop.failed()) {
				throw new RuntimeException(drop.cause());
			}

			List<JsonObject> users = new LinkedList<>();

			users.add(new JsonObject().put("username", "pmlopes").put("firstName", "Paulo").put("lastName", "Lopes")
		      .put("address", "The Netherlands"));

			users.add(new JsonObject().put("username", "timfox").put("firstName", "Tim").put("lastName", "Fox").put("address",
		      "The Moon"));

			for (JsonObject user : users) {
				db.insert("users", user, res -> {
			    System.out.println("inserted " + user.encode());
		    });
			}
		});
	}

}
