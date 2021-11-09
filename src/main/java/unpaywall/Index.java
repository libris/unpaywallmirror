package unpaywall;

import java.io.File;

public class Index {

    /*
    Because Java works the way it works, there's no direct way to store an array of instances.
    Java will instead happily give you an array of pointers to instances, which isn't what we
    want, because the extra pointers will take 4 (or 8!) bytes each, almost doubling (for this case)
    the memory needed.

    Instead, store each table element as a sequence of 6 bytes, like so:
    table[n+0] = least significant byte of file number
    table[n+1] = middle significant byte of file number
    table[n+2] = most significant byte of file number
    table[n+3] = least significant byte of offset in file
    table[n+4] = middle significant byte of offset in file
    table[n+5] = most significant byte of offset in file
    Where n = 6 * element-number.

    Given that the raw Unpaywall data has around 130 million entries, we will need around ~800Mb of
    memory to index all of it. Given that an open addressing hash table should not exceed 70% of its
    load capacity, we need to actually use ~1.2Gb. In order for this to work well over time (as the
    data grows), let's just size the table at 2Gb and call it a day. Coincidence: This just happens
    to be the maximum size of arrays Java allow (lol?). If/when that's no longer enough, index "int"s
    instead of "bytes". This will waste 2 extra bytes per entry, but that cannot be helped within the
    confines of Java.
     */

    byte[] m_table;

    public void indexDirectory(String path) {
        m_table = new byte[2147483647]; // 2Gb
        File directory = new File(path);
        for (File f : directory.listFiles()) {
            if (!f.isDirectory()) {
                // TODO: IN PARALLEL!
                indexFile(f);
            }
        }
    }

    private void indexFile(File file) {

    }
}
