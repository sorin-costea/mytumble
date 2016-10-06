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
import io.vertx.ext.mongo.FindOptions;
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
		eb.<String>consumer("mytumble.mongo.resetusers").handler(this::resetUsers);
	}

	@Suspendable
	private void resetUsers(Message<String> msg) {
		vertx.<String>executeBlocking(fiberHandler(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				JsonObject update = new JsonObject().put("$set",
				        new JsonObject().put("followsme", false).put("ifollow", false));
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
				JsonObject user = msg.body();
				client.save("users", user, h -> {
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
				ArrayList<Object> users = Lists.newArrayList(msg.body());
				int total = users.size();
				logger.info("Saving users: " + total);
				int followsme = 0;
				int ifollow = 0;
				for (Object user : users) {
					if (((JsonObject) user).getBoolean("ifollow") != null && ((JsonObject) user).getBoolean("ifollow"))
						ifollow++;
					if (((JsonObject) user).getBoolean("followsme") != null
					        && ((JsonObject) user).getBoolean("followsme"))
						followsme++;
					// await, to not kill Mongo's thread pool
					String res = awaitResult(h -> client.save("users", (JsonObject) user, h));
					if (res != null) { // not sure what that means
						logger.error(res);
					}
					List<JsonObject> found = awaitResult(h -> client.find("users", (JsonObject) user, h));
					if (found.size() != 1) {
						logger.error(found.size() + " for user " + ((JsonObject) user).getString("name"));
					}
				}
				logger.info("ifollow " + ifollow);
				logger.info("followsme " + followsme);
				future.complete();
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
			FindOptions options = new FindOptions().setSort(new JsonObject().put("name", 1));
			client.findWithOptions("users", query, options, res -> {
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
