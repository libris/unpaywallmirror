package oamirror;

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

    // The fileNumber int does double duty: we use the 21 low bits for the actual file number,
    // and the 11 high bits for a DOI "fingerprint" that we can compare when probing,
    // so we can reject candidates without having to fetch from DISK and parse JSON.
    // 21 bits for fileNumber means a max of 2097151 files. Current Crossref dump has ~1.3M files,
    // so plenty of space still. If we ever need more we'd also have to increase tableSize anyway
    // (meaning reindex) and could give another bit to file number and one fewer to the fingerprint
    // and still be fine.
    static final int FILE_NUMBER_BITS = 21;
    static final int FILE_NUMBER_MASK = (1 << FILE_NUMBER_BITS) - 1;
    static final int MAX_FILE_NUMBER = FILE_NUMBER_MASK;
    static final int FINGERPRINT_BITS = 32 - FILE_NUMBER_BITS; // 11
    static final int FINGERPRINT_MASK = (1 << FINGERPRINT_BITS) - 1;

    /**
     * Secondary hash independent of String.hashCode(), used as a fingerprint stored alongside
     * each slot. We multiply-mix the chars with a different multiplier (33 vs hashCode's 31) and
     * a finalisation step, so a chain that clusters under hashCode() does NOT also cluster
     * under this function.
     */
    static int fingerprintFor(String doi) {
        int h = 0;
        for (int i = 0; i < doi.length(); ++i) {
            h = h * 33 + doi.charAt(i);
        }
        // Based on:
        // https://nullprogram.com/blog/2018/07/31/#update-after-one-week
        // https://github.com/skeeto/hash-prospector (public domain)
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        int fp = h & FINGERPRINT_MASK;
        return fp == 0 ? 1 : fp;
    }

    public byte[] getByDoi(String doi) throws IOException {
        int hash = Math.abs(doi.hashCode());
        int tableIndex = hash % (tableSize / 2);
        int expectedFp = fingerprintFor(doi);

        int linearProbe = 0;
        int packed;
        while ( (packed = table[ ((tableIndex + linearProbe) * 2 + 0) % tableSize ]) != 0 ) {
            ++linearProbe;

            // Memory-only fingerprint check: rejects ~2047/2048 of unrelated DOIs sharing
            // this cluster without touching disk.
            int slotFp = (packed >>> FILE_NUMBER_BITS) & FINGERPRINT_MASK;
            if (slotFp != expectedFp) continue;

            int fileNumber = packed & FILE_NUMBER_MASK;
            int offset = table[ ((tableIndex + linearProbe - 1) * 2 + 1) % tableSize ];

            byte[] entry = getEntryAt(fileNumber, offset);
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

    private byte[] getEntryAt(int fileNumber, int offset) throws IOException {
        String fileName = String.format("%08d.gz", fileNumber);
        try (GZIPInputStream in = new GZIPInputStream(
                new BufferedInputStream(new FileInputStream(path+"/"+fileName)))) {
            in.skipNBytes(offset);
            ByteArrayOutputStream entry = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                for (int i = 0; i < n; ++i) {
                    if (buf[i] == 10) { // terminate on LF
                        entry.write(buf, 0, i);
                        return entry.toByteArray();
                    }
                }
                entry.write(buf, 0, n);
            }
            return entry.toByteArray(); // last entry (EOF)
        }
    }

    private void indexFile(File file) throws IOException {
        int fileNumber = Integer.parseInt(file.getName().substring(0, 8));
        if (fileNumber <= 0 || fileNumber > MAX_FILE_NUMBER) {
            throw new IOException("File number " + fileNumber + " from " + file.getName()
                    + " is out of range [1, " + MAX_FILE_NUMBER + "]; bump FILE_NUMBER_BITS.");
        }
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
                    int fp = fingerprintFor(doi);
                    int packed = (fp << FILE_NUMBER_BITS) | fileNumber;
                    int linearProbe = 0;
                    while ( table[ ((tableIndex + linearProbe) * 2 + 0) % tableSize ] != 0 ) {
                        ++linearProbe;
                    }
                    table[ ((tableIndex + linearProbe) * 2 + 0) % tableSize ] = packed;
                    table[ ((tableIndex + linearProbe) * 2 + 1) % tableSize ] = offset;

                    ++indexCount;
                    if ( (float) indexCount > ((tableSize / 2.0f) * 0.7f) ) {
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

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path+"/index"), 1 << 20))) {
            for (int i = 0; i < tableSize; ++i) {
                out.writeInt(table[i]);
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

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(f), 1 << 20))) {
            for (int i = 0; i < tableSize; ++i) {
                table[i] = in.readInt();
            }
        }

        System.err.println("Done.");
        return true;
    }
}
