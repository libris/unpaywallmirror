package unpaywall;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class Api extends HttpServlet {
    public void init() {
        Index index = new Index();
        index.indexDirectory("/tmp/splittest");
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    }
}
