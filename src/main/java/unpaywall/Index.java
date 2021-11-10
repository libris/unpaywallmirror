package unpaywall;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.codehaus.jackson.map.ObjectMapper;

public class Index {

    /*
    This class represents a table, where an ID is mapped to a (file+offset) pair, where
    the relevant entry is stored. In order to not consume extravagant amounts of memory by also
    storing the full key strings in the table, the index is allowed to output extra (incorrect)
    entries in the case of a hash collision (or linear probe overlap). These extra entries are
    easily filtered out before returning.

    Because Java works the way it works, there's no direct way to store an array of instances.
    Java will instead happily give you an array of pointers to instances, which isn't what we
    want, because the extra pointers will take 4 (or 8!) bytes each, almost doubling (for this case)
    the memory needed.

    Instead, store each table element as a pair of integers, the first containing the file number
    and the second containing the offset in the file.

    Ideally, we would use a byte[] instead and only 3 bytes for each number, which is enough. But this
    becomes unsustainable (in Java) because we would (soon) need an address space for the array that
    could exceed Integer.MAX, which Java does not allow. Because of this we're forced to waste 2 bytes
    per element (which becomes ~300Mb of wasted memory for the index as a whole).

    Given that the raw Unpaywall data has around 130 million entries, we will need just over 1Gb of
    memory to index all of it. Given that an open addressing hash table should not exceed 70% of its
    load capacity, we need to actually use ~1.5Gb. In order for this to work well over time (as the
    data grows), let's just size the table at 2Gb and call it a day.
     */

    int[] table;
    final int tableSize = 536870912; // This number of ints = 2Gb ( 2*1024*1024*1024/4 )
    final ObjectMapper mapper = new ObjectMapper();
    final String path;
    int indexCount = 0;

    public String getByDoi(String doi) throws IOException {
        int hash = Math.abs(doi.hashCode());
        int tableIndex = hash % (tableSize / 2);

        int linearProbe = 0;
        while ( table[ (tableIndex + linearProbe) * 2 + 0 ] != 0 ) {
            int fileNumber = table[ (tableIndex + linearProbe) * 2 + 0 ];
            int offset = table[ (tableIndex + linearProbe) * 2 + 1 ];
            ++linearProbe;

            // If this is the one, return it!
            String entry = getEntryAt(fileNumber, offset);
            Map json = mapper.readValue(entry, HashMap.class);
            String candidateDoi = (String) json.get("doi");
            if (candidateDoi.equals(doi))
                return entry;
        }

        return null;
    }

    public Index(String path) throws IOException {
        this.path = path;
        table = new int[tableSize]; // All initial zeros, by lang spec

        if (!loadIndexFromFile()) {
            // Build an index
            File directory = new File(path);
            for (File f : directory.listFiles()) {
                if (!f.isDirectory()) {
                    // TODO: IN PARALLEL?
                    indexFile(f);
                }
            }
            writeIndexToFile();
        }
    }

    private String getEntryAt(int fileNumber, int offset) throws IOException {
        String fileName = String.format("%08d.gz", fileNumber);
        try (GZIPInputStream in = new GZIPInputStream(new FileInputStream(path+"/"+fileName))) {
            in.skipNBytes(offset);
            byte[] data = in.readAllBytes();
            for (int i = 0; i < data.length; ++i) {
                if (data[i] == 10 || i == data.length - 1) { // = LF (\n) or EOF
                    return new String(data, 0, i, StandardCharsets.UTF_8);
                }
            }
        }
        return null; // can't happen
    }

    private void indexFile(File file) throws IOException {
        int fileNumber = Integer.parseInt(file.getName().substring(0, 8));
        try (GZIPInputStream in = new GZIPInputStream(new FileInputStream(file))) {
            byte[] data = in.readAllBytes();

            int entryBeginsAt = 0;
            for (int i = 0; i < data.length; ++i) {
                if (data[i] == 10 || i == data.length - 1) { // = LF (\n) or EOF
                    // The current line is now in between entryBeginsAt and i
                    String line = new String(data, entryBeginsAt, i-entryBeginsAt, StandardCharsets.UTF_8);
                    Map json = mapper.readValue(line, HashMap.class);
                    String doi = (String) json.get("doi");

                    // insert into index
                    int hash = Math.abs(doi.hashCode());
                    int tableIndex = hash % (tableSize / 2);
                    int offset = entryBeginsAt;
                    int linearProbe = 0;
                    while ( table[ (tableIndex + linearProbe) * 2 + 0 ] != 0 ) {
                        ++linearProbe;
                    }
                    table[ (tableIndex + linearProbe) * 2 + 0 ] = fileNumber;
                    table[ (tableIndex + linearProbe) * 2 + 1 ] = offset;

                    ++indexCount;

                    if ( (float) indexCount > (tableSize / 2.0f * 0.7f) ) {
                        System.err.println("WARNING! The index is filled to above 70% of capacity. You need to increase the 'tableSize' variable!");
                    }

                    //System.err.println("Indexing doi: " + doi + " in file number: " + fileNumber + " at offset: " + entryBeginsAt);
                    entryBeginsAt = i+1;
                }
            }
        }
    }

    private void writeIndexToFile() throws IOException {
        System.err.println("Writing index to: " + path + "/index ..");

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path+"/index"))) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            for (int i = 0; i < tableSize; ++i) {
                buf.putInt(table[i]);
                buf.clear(); // sigh
                out.write(buf.array());
            }
        }

        System.err.println("Done.");
    }

    private boolean loadIndexFromFile() throws IOException {
        File f = new File(path+"/index");
        if (!f.exists()) {
            System.err.println("No index available.");
            return false;
        }

        System.err.println("Loading index from: " + path + "/index ..");

        byte[] b = new byte[4];
        ByteBuffer buf = ByteBuffer.wrap(b);
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
            for (int i = 0; i < tableSize; ++i) {
                buf.clear();
                in.readNBytes(b, 0, 4);
                table[i] = buf.getInt();
            }
        }

        System.err.println("Done.");
        return true;
    }
}
