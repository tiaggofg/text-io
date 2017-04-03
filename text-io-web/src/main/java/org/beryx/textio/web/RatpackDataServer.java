/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.beryx.textio.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.guice.Guice;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.session.Session;
import ratpack.session.SessionModule;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * A Ratpack-based web server that allows clients to access the {@link DataApi}.
 */
public class RatpackDataServer extends AbstractDataServer {
    private static final Logger logger =  LoggerFactory.getLogger(WebTextTerminal.class);

    private int port;
    private String baseDir;
    private final Function<String, DataApi> dataApiProvider;

    public RatpackDataServer(Function<String, DataApi> dataApiProvider) {
        this.dataApiProvider = dataApiProvider;
    }

    public RatpackDataServer withBaseDir(String baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    public RatpackDataServer withPort(int portNumber) {
        this.port = portNumber;
        return this;
    }
    public int getPort() {
        return port;
    }

    public void init() {
        try {
            RatpackServer.start(server -> {
                server.serverConfig(c -> {
                    if(port > 0) {
                        c.port(port);
                    }
                    if(baseDir != null) {
                        c.baseDir(BaseDir.find(baseDir));
                    }
                });
                server.registry(Guice.registry(b -> b.module(SessionModule.class)));
                server.handlers(chain -> chain
                                .get(getPathForGetData(), ctx -> {
                                    logger.trace("Received GET");
                                    ctx.getResponse().contentType("application/json");
                                    ctx.render(handleGetData(getDataApi(ctx)));
                                })
                                .post(getPathForPostInput(), ctx -> {
                                    logger.trace("Received POST");
                                    DataApi dataApi = getDataApi(ctx);
                                    Request request = ctx.getRequest();
                                    boolean userInterrupt = Boolean.parseBoolean(request.getHeaders().get("textio-user-interrupt"));
                                    request.getBody().then(req -> {
                                        String result = handlePostInput(dataApi, req.getText(Charset.forName("UTF-8")), userInterrupt);
                                        ctx.getResponse().send(result);
                                    });
                                })
                                .get(":name", ctx -> {
                                    String resName = "/public-html/" + ctx.getPathTokens().get("name");
                                    URL res = getClass().getResource(resName);
                                    if(res != null) {
                                        Path path = Paths.get(res.toURI());
                                        ctx.getResponse().sendFile(path);
                                    }
                                })
                );
            });
        } catch (Exception e) {
            logger.error("Ratpack failure", e);
        }
    }

    protected static String getId(Context ctx) {
        String id = ctx.get(Session.class).getId();
        String uuid = ctx.getRequest().getHeaders().get("uuid");
        if(uuid != null) {
            id += "-" + uuid;
        }
        logger.trace("id: {}", id);
        return id;
    }

    protected DataApi getDataApi(Context ctx) {
        String id = getId(ctx);
        return dataApiProvider.apply(id);
    }
}