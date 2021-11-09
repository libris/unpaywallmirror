# Unpaywall mirror

This creates a local mirror of the Unpaywall dataset API, based on one of their dump files.

Getting entries by DOI is the only supported action. There is no searching. 

#### Ingest a dump
To ingest a dump, first make sure you've permission to download one from Unpaywall.
When you've obtained a download link do the following:

1. Shut the service down
1. Delete or move anything (like older dumps) already in place at ```$DEST_DIR_WITH_TRAILING_SLASH```, and make sure the directory exists and is writable.
1. ``` $ wget -O - $DOWNLOAD_URL | split -l 128 --numeric-suffixes=1 --suffix-length=8 --filter='gzip > $FILE.gz' - $DEST_DIR_WITH_TRAILING_SLASH ```
1. Start the service up again

The first time the service starts (with a new datadump) it will spend some time building an index of the dump. This will only happen once.

#### Local development

If you need to test things out or make some changes, and have a dump or a part of one, use the head command to limit the amount of data you need to work with, like so:
```
$ zcat unpaywall_snapshot_2021-07-02T151134.jsonl.gz | head -10000 | split -l 128 --numeric-suffixes=1 --suffix-length=8 --filter='gzip > $FILE.gz' - /tmp/splittest/
```
