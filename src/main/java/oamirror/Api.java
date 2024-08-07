package oamirror;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;

public class Api extends HttpServlet {

    Index index;
    boolean indexIsAvailable = false;

    public void init() {
        try {
            String dataSource = getServletConfig().getInitParameter("datasource");
            String dataLocation = System.getProperty(dataSource+".datadir");
            System.err.println("Running with '" + dataSource + "' configured data location: " + dataLocation);
            if (dataLocation == null || dataLocation.equals("")) {
                System.err.println("No data directory specified for '" + dataSource + "', index will be unavailable.");
                return;
            }
            index = new Index(dataLocation);
            synchronized (this) {
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
                out.write("The index is unavailable.");
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
                    res.setContentType("application/json");
                    try (OutputStreamWriter out = new OutputStreamWriter(res.getOutputStream())) {
                        out.write(responseValue);
                    }
                }
            } else {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }
}
