## This is an attempt to write few tools for hbase
### -  (work in progress)

* Create a test environment (Optional)

``` shell
# Create home dir for hbase user
kinit -kt $(ls -tr /var/run/cloudera-scm-agent/process/*/hdfs.keytab|tail -1) hdfs/$(hostname -f)
hdfs dfs -mkdir /user/hbase
hdfs dfs -chown hbase:hbase /user/hbase
hdfs dfs -chmod 770 /user/hbase
```

* Create a test table with few regions and fill it with test data (Optional)

``` shell

kinit -kt $(ls -tr /var/run/cloudera-scm-agent/process/*/hbase.keytab|tail -1) hbase/$(hostname -f)  

export TABLE='default:T'

hbase ltt -tn ${TABLE} \
          -init_only \
          -compression SNAPPY \
          -families 0,1 \
          -num_regions_per_server 5

cat<<EOF|hbase shell
#Use the below to set/unset table properties.
# MAX_FILESIZE - default 10GB
# SPLIT_POLICY - default org.apache.hadoop.hbase.regionserver.IncreasingToUpperBoundRegionSplitPolicy
#     - org.apache.hadoop.hbase.regionserver.ConstantSizeRegionSplitPolicy
#     - org.apache.hadoop.hbase.regionserver.IncreasingToUpperBoundRegionSplitPolicy
#     - org.apache.hadoop.hbase.regionserver.SteppingSplitPolicy
# MEMSTORE_FLUSHSIZE - default 128MB
# COMPACTION_ENABLED
# FLUSH_POLICY
# alter '${TABLE}', MAX_FILESIZE => '100000000'
# alter '${TABLE}', METHOD => 'table_att_unset', NAME => 'MAX_FILESIZE'

alter '${TABLE}', MAX_FILESIZE => '100000000'
alter '${TABLE}', MEMSTORE_FLUSHSIZE => '20000000'
alter '${TABLE}', SPLIT_POLICY => 'org.apache.hadoop.hbase.regionserver.ConstantSizeRegionSplitPolicy'
desc '${TABLE}'
EOF


hbase ltt -tn ${TABLE} \
          -skip_init \
          -families 0,1 \
          -write 5:5000 \
          -num_keys 100000
```

* Few environment variables used

``` shell
#Source table
export ST=default:T
export DT=default:T1

export ST_PREFIX=$(echo ${ST}|sed 's/:/_/')
export DT_PREFIX=$(echo ${DT}|sed 's/:/_/')

export ROWKEYS=/tmp/${ST_PREFIX}_keys
export ROWKEYS_RND=/tmp/${ST_PREFIX}_keys_rnd
export READ_RND=/tmp/${ST_PREFIX}_read_rnd
export ROWKEYS_SAMPLE=/tmp/${ST_PREFIX}_keys_sample
export SCAN=/tmp/${ST_PREFIX}_scan
export BULKLOAD=/tmp/${DT_PREFIX}_bulkload

export JAR=/root/hbase-tools/target/hbase-tools.jar
export CLASSPATH=`hadoop classpath`:`hbase mapredcp`:/etc/hbase/conf:$JAR
export HADOOP_CLASSPATH=${CLASSPATH}
```

* Test random reads
``` shell
#### export all table keys (just the rowkeys)
## uses KeyOnlyFilter
hdfs dfs -rmr -skipTrash ${ROWKEYS}
yarn jar ${JAR} \
   com.cloudera.ps.hbasestuff.GetRowKeys \
   -Dhbase.client.scanner.caching=100 \
   -Dmapreduce.map.speculative=false \
   -Dmapreduce.reduce.speculative=false \
   -Dmapreduce.output.fileoutputformat.compress=true \
   -Dmapreduce.output.fileoutputformat.compress.codec=org.apache.hadoop.io.compress.SnappyCodec \
   -Dmapreduce.output.fileoutputformat.compress.type=BLOCK   \
   -Dmapreduce.job.reduces=0 \
    --tableName ${ST} \
    --outputPath ${ROWKEYS}   

#### Shuffle keys
## This step is necessary to randomize the keys order and extract just a subset of the keys to test
## --samplePercent - tries to only use a percentage of the keys
hdfs dfs -rmr -skipTrash ${ROWKEYS_RND}
yarn jar ${JAR} \
   com.cloudera.ps.hbasestuff.ShuffleKeys \
   -Dmapreduce.map.speculative=false \
   -Dmapreduce.reduce.speculative=false \
   -Dmapreduce.output.fileoutputformat.compress=true \
   -Dmapreduce.output.fileoutputformat.compress.codec=org.apache.hadoop.io.compress.SnappyCodec \
   -Dmapreduce.output.fileoutputformat.compress.type=BLOCK   \
   -Dmapreduce.job.reduces=10 \
    --samplePercent 10 \
    --inputPath ${ROWKEYS} \
    --outputPath ${ROWKEYS_RND}   

#### Random reads
##Export data from table using random reads
hdfs dfs -rmr -skipTrash ${READ_RND}
yarn jar ${JAR} \
   com.cloudera.ps.hbasestuff.Read \
   -Dmapreduce.job.running.map.limit=1000 \   
   --keysPath ${ROWKEYS_RND} \
   --tableName ${ST} \
   --outputPath ${READ_RND} 
```

* Test random writes
``` shell
#### Random writes
## Import data that was read previously
hbase org.apache.hadoop.hbase.mapreduce.Import \
   -Dmapreduce.map.speculative=false \
   -Dmapreduce.reduce.speculative=false \
   -Dmapreduce.job.running.map.limit=1000 \
   -Dmapreduce.map.memory.mb=8192 \
   ${DT} \
   ${READ_RND}
```

* Test scan reads
```
#### Scan
#### this is just a regular table export
hdfs dfs -rmr -skipTrash ${SCAN}
hbase org.apache.hadoop.hbase.mapreduce.Export \
   -Dhbase.client.scanner.caching=100 \
   -Dmapreduce.map.speculative=false \
   -Dmapreduce.reduce.speculative=false \
   -Dmapreduce.output.fileoutputformat.compress=true \
   -Dmapreduce.output.fileoutputformat.compress.codec=org.apache.hadoop.io.compress.SnappyCodec \
   -Dmapreduce.output.fileoutputformat.compress.type=BLOCK   \
   -Dmapreduce.job.running.map.limit=1000 \
   -Ddfs.replication=1 \
   -Dmapreduce.map.memory.mb=8192 \
   ${ST} \
   ${SCAN}
```

* How to re-split an existing table into a different (but sort of equal) number of regions

```
#### Export sample keys
##export table keys and the row sizes.
##This can be used to analyze the table later.
##--includeRowSize - get a sample of the size along with the count
hdfs dfs -rmr -skipTrash ${ROWKEYS_SAMPLE}
yarn jar ${JAR} \
   com.cloudera.ps.hbasestuff.SampleKeys \
   -Dhbase.client.scanner.caching=100 \
   -Dmapreduce.map.speculative=false \
   -Dmapreduce.reduce.speculative=false \
   -Dmapreduce.job.reduces=0 \
   -Dmapreduce.output.fileoutputformat.compress=true \
   -Dmapreduce.output.fileoutputformat.compress.codec=org.apache.hadoop.io.compress.SnappyCodec \
   -Dmapreduce.output.fileoutputformat.compress.type=BLOCK   \
    --tableName ${ST} \
    --outputPath ${ROWKEYS_SAMPLE} \
    --includeRowSize \
    --sampleCount 100

#### Calculate new split points and create a new table
##export table keys and the row sizes.
yarn jar ${JAR} \
   com.cloudera.ps.hbasestuff.CalculateSplits \
   --keysPath ${ROWKEYS_SAMPLE} \
   --regions 40 \
   --bySize \
   --tableName ${DT} \
   --families 0:1



####Table import - bulkload
hbase org.apache.hadoop.hbase.mapreduce.Import \
   -Dmapreduce.map.speculative=false \
   -Dmapreduce.reduce.speculative=false \
   -Dmapreduce.job.running.map.limit=1000 \
   -Dmapreduce.map.memory.mb=8192 \
   -Dmapreduce.job.split.metainfo.maxsize=100000000\
   -Dimport.bulk.output=${BULKLOAD} \
    ${DT} \
    ${SCAN}

hbase org.apache.hadoop.hbase.mapreduce.LoadIncrementalHFiles \
    ${BULKLOAD} \
    ${DT} 

## or
####Table import - puts
hbase org.apache.hadoop.hbase.mapreduce.Import \
   -Dmapreduce.map.speculative=false \
   -Dmapreduce.reduce.speculative=false \
   -Dmapreduce.job.running.map.limit=1000 \
   -Dmapreduce.map.memory.mb=8192 \
   -Dmapreduce.job.split.metainfo.maxsize=100000000\
    ${DT} \
    ${SCAN}
```

