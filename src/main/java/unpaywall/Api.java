package unpaywall;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;

// TODO: Perhaps use a read-write type lock, to make sure the table is written before reading it. Doing so should also guard against not-in-my-cache problems.

public class Api extends HttpServlet {

    Index index;
    boolean indexIsAvailable = false;

    public void init() {
        try {
            synchronized (this) {
                index = new Index("/tmp/splittest");
                indexIsAvailable = true;
            }

            // TEST
            System.out.println("Retrieving:\n" + index.getByDoi("10.1007/978-3-531-92639-1_25"));
        } catch (Throwable e) {
            // TODO
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

                System.err.println("Doing request for "+doi);

                String responseValue = index.getByDoi(doi);
                if (responseValue == null) {
                    res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                } else {
                    System.err.println("Sending response");
                    res.setStatus(HttpServletResponse.SC_OK);
                    try (OutputStreamWriter out = new OutputStreamWriter(res.getOutputStream())) {
                        out.write(responseValue);
                    }
                }
            }
        }
    }
}
