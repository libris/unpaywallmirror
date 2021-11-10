package unpaywall;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// TODO: Perhaps use a read-write type lock, to make sure the table is written before reading it. Doing so should also guard against not-in-my-cache problems.

public class Api extends HttpServlet {
    public void init() {
        Index index;
        try {
            index = new Index("/tmp/splittest");

            // TEST
            System.out.println("Retrieving:\n" + index.getByDoi("10.1007/978-3-531-92639-1_25"));
        } catch (Throwable e) {
            // TODO
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    }
}
