package org.sorincos.mytumble;

import static io.vertx.ext.sync.Sync.awaitResult;
import static io.vertx.ext.sync.Sync.fiberHandler;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
		eb.<JsonArray>consumer("mytumble.mongo.getusers").handler(this::getUsers);
		eb.<JsonArray>consumer("mytumble.mongo.getuser").handler(this::getUser);
		eb.<String>consumer("mytumble.mongo.resetfollowers").handler(this::resetFollowers);

		eb.<JsonArray>consumer("mytumble.mongo.saveposts").handler(this::savePosts);
		eb.<JsonArray>consumer("mytumble.mongo.getposts").handler(this::getPosts);
	}

	@Suspendable
	private void resetFollowers(Message<String> msg) {
		vertx.<String>executeBlocking(fiberHandler(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				JsonObject update = new JsonObject().put("$set", new JsonObject().put("followsme", false));
				MongoClientUpdateResult res = awaitResult(h -> client.updateCollectionWithOptions("users", new JsonObject(),
				    update, new UpdateOptions().setMulti(true), h));
				logger.info("Users upsert: " + res.getDocModified());
				future.complete("Users upsert: " + res.getDocModified());
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
					    new JsonObject().put("name", upsert.getJsonObject("$set").getString("name")), upsert, options, h));
					logger.info("Users upsert: " + res.getDocModified());
					total -= res.getDocModified();
				}
				if (total > 0) {
					future.fail("Some users failed to update: " + total + " of " + users.size());
				}
				future.complete();
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

	@Deprecated
	@Suspendable
	private void savePosts(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(fiberHandler(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				UpdateOptions options = new UpdateOptions().setUpsert(true);
				ArrayList<Object> posts = Lists.newArrayList(msg.body());
				int total = posts.size();
				for (Object post : posts) {
					JsonObject upsert = new JsonObject().put("$set", (JsonObject) post);
					// await, to not kill Mongo's thread pool with the foreach
					MongoClientUpdateResult res = awaitResult(h -> client.updateCollectionWithOptions("posts",
					    new JsonObject().put("postid", upsert.getJsonObject("$set").getLong("postid")), upsert, options, h));
					total -= res.getDocModified();
				}
				if (total > 0) {
					future.fail("Some posts failed to update: " + total + " of " + posts.size());
				}
				future.complete();
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

	private void getUsers(Message<JsonArray> msg) {
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

	@Deprecated
	private void getPosts(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			MongoClient client = vertx.getOrCreateContext().get("mongoclient");
			System.out.println("and");
			client.find("posts", new JsonObject(), res -> {
				if (res.failed()) {
					future.fail(res.cause().getLocalizedMessage());
				}
				final JsonArray posts = new JsonArray();
				res.result().forEach(posts::add);
				future.complete(posts);
			});
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private JsonObject createQueryFromFilters(JsonArray filters) {
		JsonObject query = new JsonObject();
		for (Object filter : filters) {
			if ("special".compareToIgnoreCase((String) filter) == 0)
				query.put("special", true);
			if ("notspecial".compareToIgnoreCase((String) filter) == 0)
				query.put("special", false);
			if ("weird".compareToIgnoreCase((String) filter) == 0)
				query.put("weirdo", true);
			if ("notweird".compareToIgnoreCase((String) filter) == 0)
				query.put("weirdo", false);
			if ("followsme".compareToIgnoreCase((String) filter) == 0)
				query.put("followsme", true);
			if ("notfollowsme".compareToIgnoreCase((String) filter) == 0)
				query.put("followsme", false);
			if ("ifollow".compareToIgnoreCase((String) filter) == 0)
				query.put("ifollow", true);
			if ("notifollow".compareToIgnoreCase((String) filter) == 0)
				query.put("ifollow", false);
			if ("likers".compareToIgnoreCase((String) filter) == 0)
				query.put("liker", true);
		}
		return query;
	}

}
