package unpaywall;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;

public class Api extends HttpServlet {

    Index index;
    boolean indexIsAvailable = false;

    public void init() {
        try {
            synchronized (this) {
                index = new Index(System.getProperty("unpaywall.datadir"));
                indexIsAvailable = true;
            }
        } catch (Throwable e) {
            System.err.println("Init failed with: " + e);
            e.printStackTrace(new PrintStream(System.err));
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {

        /*
        The point of this little dance isn't (primarily) to protect 'indexIsAvailable'.
        It is to trigger a happens-before-guarantee on the index itself. Without this, it
        could (in theory, not in practice) be that the index having been written by thread A,
        was still seen as all zeros by thread B. This way, all threads can read the index
        directly and simultaneously, without locking it.
         */
        boolean available = false;
        synchronized (this) {
            available = indexIsAvailable;
        }

        res.setCharacterEncoding("UTF-8");

        if (!available) {
            res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            try (OutputStreamWriter out = new OutputStreamWriter(res.getOutputStream())) {
                out.write("The index is being (re)built, please try again later.");
            }
        } else {
            if (req.getPathInfo() != null) {
                // We expect "/some:funky_doi"
                String doi = req.getPathInfo();
                while (doi.startsWith("/"))
                    doi = doi.substring(1);

                String responseValue = index.getByDoi(doi);
                if (responseValue == null) {
                    res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                } else {
                    res.setStatus(HttpServletResponse.SC_OK);
                    try (OutputStreamWriter out = new OutputStreamWriter(res.getOutputStream())) {
                        out.write(responseValue);
                    }
                }
            }
        }
    }
}
