package org.sorincos.mytumble;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;

import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.springframework.boot.autoconfigure.web.FiberSpringBootApplication;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

@FiberSpringBootApplication
public class VertxBootApplication {

    @Autowired
    private WebServer webServer;

    @Autowired
    private TumblrConnector tumblrConnector;

    @Autowired
    private MongoConnector mongoConnector;

    @PostConstruct
    @Suspendable
    public void deployVerticle() {
        VertxOptions options = new VertxOptions();
        options.setBlockedThreadCheckInterval(1000 * 60 * 60);
        Vertx vertx = Vertx.vertx(options);
        vertx.deployVerticle(webServer);
        vertx.deployVerticle(mongoConnector);
        vertx.deployVerticle(tumblrConnector);
    }

    @Suspendable
    public static void main(String[] args) {
        SpringApplication.run(VertxBootApplication.class, args);
    }
}
