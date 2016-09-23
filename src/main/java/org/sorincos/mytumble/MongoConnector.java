package org.sorincos.mytumble;

import static io.vertx.ext.sync.Sync.awaitResult;
import static io.vertx.ext.sync.Sync.fiberHandler;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import co.paralleluniverse.fibers.Suspendable;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientUpdateResult;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.ext.sync.SyncVerticle;

@Component
@ConfigurationProperties(prefix = "database")
public class MongoConnector extends SyncVerticle {
	static final Logger logger = LoggerFactory.getLogger(MongoConnector.class);

	private String name;

	public String getName() {
		return name;
	}

	public void setName(String database) {
		this.name = database;
	}

	@Override
	public void start() throws Exception {
		MongoClient mongo = MongoClient.createShared(vertx, new JsonObject().put("db_name", name));
		vertx.getOrCreateContext().put("mongoclient", mongo);

		EventBus eb = vertx.eventBus();
		eb.<JsonArray>consumer("mytumble.mongo.saveusers").handler(this::saveUsers);
		eb.<JsonObject>consumer("mytumble.mongo.saveuser").handler(this::saveUser);
		eb.<String>consumer("mytumble.mongo.getusers").handler(this::getUsers);
		eb.<JsonArray>consumer("mytumble.mongo.getuser").handler(this::getUser);
		eb.<String>consumer("mytumble.mongo.resetfollowers").handler(this::resetFollowers);
	}

	@Suspendable
	private void resetFollowers(Message<String> msg) {
		vertx.<String>executeBlocking(fiberHandler(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				JsonObject update = new JsonObject().put("$set", new JsonObject().put("followsme", false));
				MongoClientUpdateResult res = awaitResult(h -> client.updateCollectionWithOptions("users",
				        new JsonObject(), update, new UpdateOptions().setMulti(true), h));
				logger.info("Users reset: " + res.getDocModified());
				future.complete("Users reset: " + res.getDocModified());
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}), result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private void saveUser(Message<JsonObject> msg) {
		vertx.<JsonObject>executeBlocking(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				UpdateOptions options = new UpdateOptions().setUpsert(true);
				JsonObject user = msg.body();
				JsonObject upsert = new JsonObject().put("$set", (JsonObject) user);
				client.updateCollectionWithOptions("users",
				        new JsonObject().put("name", upsert.getJsonObject("$set").getString("name")), upsert, options,
				        h -> {
					        if (h.failed()) {
						        logger.info("User upsert failed: " + h.cause().getLocalizedMessage());
					        }
				        });
				future.complete();
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	@Suspendable
	private void saveUsers(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(fiberHandler(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				UpdateOptions options = new UpdateOptions().setUpsert(true);
				ArrayList<Object> users = Lists.newArrayList(msg.body());
				int total = users.size();
				for (Object user : users) {
					JsonObject upsert = new JsonObject().put("$set", (JsonObject) user);
					// await, to not kill Mongo's thread pool
					MongoClientUpdateResult res = awaitResult(h -> client.updateCollectionWithOptions("users",
					        new JsonObject().put("name", upsert.getJsonObject("$set").getString("name")), upsert,
					        options, h));
					total -= res.getDocModified();
				}
				if (total > 0) {
					future.fail("Some users failed to update: " + total + " of " + users.size());
				} else {
					future.complete();
				}
			} catch (Exception ex) {
				logger.error("Exception updating users: ", ex);
				future.fail(ex.getLocalizedMessage());
			}
		}), result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private void getUsers(Message<String> msg) {
		JsonObject query = createQueryFromFilters(msg.body());
		vertx.<JsonArray>executeBlocking(future -> {
			MongoClient client = vertx.getOrCreateContext().get("mongoclient");
			client.find("users", query, res -> {
				if (res.failed()) {
					future.fail(res.cause().getLocalizedMessage());
				}
				final JsonArray users = new JsonArray();
				res.result().forEach(users::add);
				future.complete(users);
			});
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private void getUser(Message<JsonArray> msg) {
		JsonObject query = new JsonObject().put("name", msg.body().getString(0));
		vertx.<JsonArray>executeBlocking(future -> {
			MongoClient client = vertx.getOrCreateContext().get("mongoclient");
			client.find("users", query, res -> {
				if (res.failed()) {
					future.fail(res.cause().getLocalizedMessage());
				}
				final JsonArray users = new JsonArray();
				res.result().forEach(users::add); // expected max one
				future.complete(users);
			});
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private JsonObject createQueryFromFilters(String filterString) {
		List<String> filters = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(filterString);
		JsonObject query = new JsonObject();
		JsonObject notTrue = new JsonObject().put("$ne", true);
		for (Object filter : filters) {
			if ("special".compareToIgnoreCase((String) filter) == 0)
				query.put("special", true);
			if ("notspecial".compareToIgnoreCase((String) filter) == 0)
				query.put("special", notTrue);
			if ("weird".compareToIgnoreCase((String) filter) == 0)
				query.put("weirdo", true);
			if ("notweird".compareToIgnoreCase((String) filter) == 0)
				query.put("weirdo", notTrue);
			if ("followsme".compareToIgnoreCase((String) filter) == 0)
				query.put("followsme", true);
			if ("notfollowsme".compareToIgnoreCase((String) filter) == 0)
				query.put("followsme", notTrue);
			if ("ifollow".compareToIgnoreCase((String) filter) == 0)
				query.put("ifollow", true);
			if ("notifollow".compareToIgnoreCase((String) filter) == 0)
				query.put("ifollow", notTrue);
			if ("likers".compareToIgnoreCase((String) filter) == 0)
				query.put("liker", true);
		}
		return query;
	}

}
