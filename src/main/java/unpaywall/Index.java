package unpaywall;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.codehaus.jackson.map.ObjectMapper;

public class Index {

    /*
    This class represents a table, where an ID is mapped to a (file+offset) pair, where
    the relevant entry is stored. In order to not consume extravagant amounts of memory by also
    storing the full key strings in the table, the index is allowed to output extra (incorrect)
    entries in the case of a hash collision. These extra entries are filtered out before returning.

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

    final int[] table;
    final int tableSize = 536870912; // This number of ints = 2Gb ( 2*1024*1024*1024/4 )
    final ObjectMapper mapper = new ObjectMapper();
    final String path;
    int indexCount = 0;

    public String getIndexedAtDoi(String doi) {
        int hash = Math.abs(doi.hashCode());
        int tableIndex = hash % (tableSize / 2);


        int linearProbe = 0;
        while ( table[ (tableIndex + linearProbe) * 2 + 0] != 0 ) {
            int fileNumber = table[ (tableIndex + linearProbe) * 2 + 0];
            int offset = table[ (tableIndex + linearProbe) * 2 + 1];
            ++linearProbe;

            // If this is the one, return it!
        }

        return null;
    }

    public Index(String path) throws IOException {
        this.path = path;
        table = new int[tableSize]; // All initial zeros, by lang spec
        File directory = new File(path);
        for (File f : directory.listFiles()) {
            if (!f.isDirectory()) {
                // TODO: IN PARALLEL!
                indexFile(f);
            }
        }
    }

    /*private String getEntryAt(int fileNumber, int offset) {

    }*/

    private void indexFile(File file) throws IOException {
        System.err.println("Scanning file: " + file.getName());
        int fileNumber = Integer.parseInt(file.getName().substring(0, 8));
        GZIPInputStream in = new GZIPInputStream(new FileInputStream(file));
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
                while ( table[ (tableIndex + linearProbe) * 2 + 0] != 0 ) {
                    ++linearProbe;
                }
                table[ (tableIndex + linearProbe) * 2 + 0] = fileNumber;
                table[ (tableIndex + linearProbe) * 2 + 1] = offset;

                ++indexCount;

                if ( (float) indexCount > (tableSize / 2.0 * 0.7) ) {
                    System.err.println("WARNING! The index is filled to above 70% of capacity. You need to increase the 'tableSize' variable!");
                }

                System.err.println("Indexing doi: " + doi + " in file number: " + fileNumber + " at offset: " + entryBeginsAt);

                entryBeginsAt = i+1;
            }
        }
    }
}
