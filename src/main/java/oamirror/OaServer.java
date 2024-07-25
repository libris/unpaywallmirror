package oamirror;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class OaServer {
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_MAX_CONNECTIONS = 200;

    public void run() throws Exception {
        var server = createServer();
        configureHandlers(server);
        server.start();
        server.join();
    }

    protected void configureHandlers(Server server) {
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        server.setHandler(context);

        // The holders+setInitOrder ensures the servlets initialize right away, rather than on the first request.
        ServletHolder unpaywallHolder = new ServletHolder(Api.class);
        unpaywallHolder.setInitOrder(0);
        unpaywallHolder.setInitParameter("datasource", "unpaywall");
        context.addServlet(unpaywallHolder, "/v2/*");

        ServletHolder crossrefHolder = new ServletHolder(Api.class);
        crossrefHolder.setInitOrder(0);
        crossrefHolder.setInitParameter("datasource", "crossref");
        context.addServlet(crossrefHolder, "/works/*");
    }

    protected Server createServer() {
        int maxConnections = Integer.parseInt(System.getProperty("oamirror.http.maxConnections", "" + DEFAULT_MAX_CONNECTIONS));
        var queue = new ArrayBlockingQueue<Runnable>(maxConnections);
        var pool = new ExecutorThreadPool(maxConnections, maxConnections, queue);

        var server = new Server(pool);

        int port = Integer.parseInt(System.getProperty("oamirror.http.port", "" + DEFAULT_HTTP_PORT));

        var httpConfig = new HttpConfiguration();
        httpConfig.setIdleTimeout(5 * 60 * 1000); // more than nginx keepalive_timeout
        httpConfig.setPersistentConnectionsEnabled(true);
        httpConfig.setCustomizers(List.of(new ForwardedRequestCustomizer()));

        try (var http = new ServerConnector(server, new HttpConnectionFactory(httpConfig))) {
            http.setPort(port);
            http.setAcceptQueueSize(maxConnections);
            server.setConnectors(new Connector[]{ http });
            System.out.println("Started server on port " + port);
        }

        server.addBean(new ConnectionLimit(maxConnections, server));

        return server;
    }

    public static void main(String[] args) throws Throwable {
        new OaServer().run();
    }
}
