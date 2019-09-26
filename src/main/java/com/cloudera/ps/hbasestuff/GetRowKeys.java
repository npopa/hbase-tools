package com.cloudera.ps.hbasestuff;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.KeyOnlyFilter;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.*;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;

public class GetRowKeys extends Configured implements Tool {
  private static final Log LOG = LogFactory.getLog(GetRowKeys.class);
  private Options options = new Options();

  private String outputPath;
  private static String table_name;
  private boolean useCache=false;

  public static class GetRowKeys1Mapper extends TableMapper<ImmutableBytesWritable, LongWritable> {
    private static LongWritable ZERO = new LongWritable(0);

    public static enum Counters {
      ROWS
    }       
    
    public void map(ImmutableBytesWritable row, Result record, Context context)
        throws IOException, InterruptedException {

        context.write(row, ZERO);        
        context.getCounter(Counters.ROWS).increment(1);
    }
  }
    

  @Override
  public int run(String[] args) throws Exception {

    String[] otherArgs = new GenericOptionsParser(getConf(), args).getRemainingArgs();

    init();
    try {
      if (!parseOptions(otherArgs))
        return 1;
    } catch (IOException ex) {

      return 1;
    }
    
    Configuration conf = getConf();
    HBaseConfiguration.merge(conf, HBaseConfiguration.create(conf));
    conf.set("GetRowKeys.tableName", table_name);
    

    final TableName tableName = TableName.valueOf(table_name);

    Job job = Job.getInstance(conf, "GetRowKeys from " + table_name + " to " + outputPath);

    Path outputDir = new Path(outputPath);
    if (outputDir.getFileSystem(getConf()).exists(outputDir)) {
      throw new IOException("Output directory " + outputDir + " already exists.");
    }
    FileOutputFormat.setOutputPath(job, outputDir);

    job.setJarByClass(GetRowKeys.class);
    Scan scan = new Scan();
    scan.setCacheBlocks(useCache);
    
    if(!useCache){
      scan.setFilter(new KeyOnlyFilter());
    }
    
    //map only
    TableMapReduceUtil.initTableMapperJob(tableName, scan, GetRowKeys1Mapper.class,
          ImmutableBytesWritable.class, LongWritable.class, job);
    job.setNumReduceTasks(0); 
    job.setOutputFormatClass(SequenceFileOutputFormat.class);
    job.setOutputKeyClass(ImmutableBytesWritable.class);
    job.setOutputValueClass(LongWritable.class);    
    return job.waitForCompletion(true) ? 0 : 1;

  }

  private void init() {

    options.addOption("o", "outputPath", true, "outputPath");
    options.addOption("t", "tableName", true, "table name ie. table1");
    options.addOption("c", "cache", false, "use it to warm the cache for the table. This is very experimental!");    
  }

  public boolean parseOptions(String args[]) throws ParseException, IOException {
    if (args.length == 0) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("ExportTableKeys", options, true);
      return false;
    }
    CommandLineParser parser = new PosixParser();
    CommandLine cmd = parser.parse(options, args);

    if (cmd.hasOption("o")) {
      outputPath = cmd.getOptionValue("o");
    }

    if (cmd.hasOption("t")) {
      table_name = cmd.getOptionValue("t");
    }
    
    if (cmd.hasOption("c")) {
      useCache=true;
    } 
    
    return true;
  }


  public static void main(String[] args) throws Exception {
    int exitCode = ToolRunner.run(new GetRowKeys(), args);
    System.exit(exitCode);
  }
}
