package org.sorincos.mytumble;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = VertxBootApplication.class)
@ConfigurationProperties(prefix = "database")
public class MongoConnectorTests {

    private String name;

    private MongoConnector mongoConnVerticle;

    @Mock
    private VertxImpl vertx;

    @Mock
    private ContextImpl context;

    @Mock
    private EventBus eventBus;

    @Mock
    private Future<Void> startFuture;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(vertx.eventBus()).thenReturn(eventBus);
        doReturn(context).when(vertx).getOrCreateContext();
        doReturn(context).when(vertx).getContext();

        mongoConnVerticle = new MongoConnector();
        mongoConnVerticle.init(vertx, context);

    }

    @After
    public void tearDown() throws Exception {
        if (mongoConnVerticle != null)
            mongoConnVerticle.stop();
    }

    @Test
    public void contextLoads() {
        Assert.assertNotNull(name);
    }

    @Test
    public void testStartValidConfig() throws Exception {
        JsonObject config = new JsonObject("{}");
        when(context.config()).thenReturn(config);

        mongoConnVerticle.start(startFuture);

        verify(context, times(1)).config();
        verify(vertx, times(1)).eventBus();
        // verify(netClient, times(1)).connect(Matchers.eq(1234), Matchers.eq("foo"),
        // Matchers.<Handler<AsyncResult<NetSocket>>>any());
    }
}
