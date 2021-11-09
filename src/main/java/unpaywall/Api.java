package unpaywall;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class Api extends HttpServlet {
    public void init() {
        Index index;
        try {
            index = new Index("/tmp/splittest");

            // TEST
            System.out.println("Retrieving:\n" + index.getIndexedAtDoi("10.1007/978-3-531-92639-1_25"));
        } catch (Throwable e) {
            // TODO
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    }
}
