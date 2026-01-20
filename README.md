# Unpaywall mirror

This creates a local mirror of the Unpaywall and Crossref dataset APIs, based on dump files.

Getting entries by DOI is the only supported action. There is no searching. 

### Ingest a dump 
This application assumes that source data is in the following form:
A directory with 8-digit-numbered gzipped files named NNNNNNNN.gz. Each file must contain a number (for example 128) of json-lines.
Each line should be a complete json-object, and must have the "doi" property (containing the DOI).
The object as a whole is what will be served when the doi is looked up.

Neither Unpaywall nor Crossref provide their data in precisely this form, and so the data needs a bit
of processing (using normal ubiquitous cli tools) before it is ready to be served.

First decide where in your filesystem you want Unpaywall and/or Crossref data placed.
You will need to make sure the servlet is then started with the following environment flags passed along (typically in JAVA_OPTS), for example like so:
```-Dunpaywall.datadir="/srv/unpaywalldata -Dcrossref.datadir="/srv/crossrefdata```

#### Unpaywall

To ingest a dump from Unpaywall, first make sure you've permission to download one from Unpaywall.
When you've obtained a download link do the following:

1. Shut the service down
1. Delete or move anything (like older dumps) already in place at ```$UNPAYWALL_DEST_DIR_WITH_TRAILING_SLASH```, and make sure the directory exists and is writable.
1. ```curl -Ss $DOWNLOAD_URL | gunzip | split -l 128 --numeric-suffixes=1 --suffix-length=8 --filter='gzip > $FILE.gz' - $UNPAYWALL_DEST_DIR_WITH_TRAILING_SLASH ``` When done from a dump on disc, expect this process to take ~2 hours. If doing it like suggested while downloading, it will obviously take longer.
1. Start the service up again

#### Crossref

1. Download a dump from crossref (this is provided as a torrent, so be careful on org networks)
1. cd to where the dump was downloaded, and make sure there is only data there (remove any robots.txt for example)
1. Shut the service down
1. Delete or move anything (like older dumps) already in place at ```$CROSSREF_DEST_DIR_WITH_TRAILING_SLASH```, and make sure the directory exists and is writable.
1. ```parallel -j$(nproc) 'pigz -dc {}' ::: *.gz | sed 's/"DOI":/"doi":/g' | split -l 128 --numeric-suffixes=1 --suffix-length=8 --filter='pigz > $FILE.gz' - $CROSSREF_DEST_DIR_WITH_TRAILING_SLASH ```
1. Start the service up again

The first time the service starts (with a new datadump) it will spend some time building an index of the dump. This will only happen once. This process may take several hours.

### Local development

If you need to test things out or make some changes, and have a dump or a part of one, use the head command to limit the amount of data you need to work with, like so:
```
zcat unpaywall_snapshot_2021-07-02T151134.jsonl.gz | head -10000 | split -l 128 --numeric-suffixes=1 --suffix-length=8 --filter='gzip > $FILE.gz' - $DEST_DIR_WITH_TRAILING_SLASH
```
And run the dev-server with
```
./gradlew appRun -Dunpaywall.datadir="$UNPAYWALL_DIR_WITHOUT_TRAILING_SLASH" -Dcrossref.datadir="$CROSSREF_DIR_WITHOUT_TRAILING_SLASH" 
```
Note that it is perfectly fine to run with only one of the dumps specified. So for example, simply do not set crossref.datadir if you wish to only mirror unpaywall data.
