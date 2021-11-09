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
        } catch (Throwable e) {
            // TODO
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    }
}
