# Unpaywall mirror

To "ingest" a dump do the following:

´zcat unpaywall_snapshot_2021-07-02T151134.jsonl.gz | head -10000 | split -l 128 --numeric-suffixes=1 --filter='gzip > $FILE.gz' - /tmp/splittest/´
