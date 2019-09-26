package com.cloudera.ps.hbasestuff;

import java.io.IOException;
import java.util.Random;
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
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.*;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;

public class ShuffleKeys extends Configured implements Tool {
  private static final Log LOG = LogFactory.getLog(ShuffleKeys.class);
  private Options options = new Options();

  private String outputPath;
  private String inputPath;
  private String samplePercent="0";
  private String sampleCount="0";

  public static class ShuffleKeysMapper extends Mapper<ImmutableBytesWritable, LongWritable, LongWritable, ImmutableBytesWritable> {
    private static LongWritable key = new LongWritable(0);   
    private long sp=0;
    private long sc=0;
    private long count=0;
    private boolean skip=false;
    private Random rand = new Random();
    public static enum Counters {
      ROWS, 
      SHUFFLED_ROWS
    }   
    
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        Configuration conf = context.getConfiguration();
        sp=conf.getLong("ShuffleKeys.samplePercent", 0);
        sc=conf.getLong("ShuffleKeys.sampleCount", 0);       
        
        LOG.info("ShuffleKeys.samplePercent="+sp);
        LOG.info("ShuffleKeys.sampleCount="+sc);       

    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
     
    }     
    
      public void map(ImmutableBytesWritable row, LongWritable value, Context context)
          throws IOException, InterruptedException {
         
        if (sp>0){ //skip by percentage
          if(rand.nextInt(100) > sp){
              skip=true;
              } else {
              skip=false;
              }
        } else if (sc>0){ //skip by count
          if((count % sc) != 0){
              skip=true;
              } else {
              skip=false;
              }
        }
            
        if (!skip){
          key.set(rand.nextLong());
          context.write(key, row);
          
          context.getCounter(Counters.ROWS).increment(1);
        }
        
        skip=false;
        count+=1;       

      }    
  }
  
  public static class ShuffleKeysReducer extends Reducer<LongWritable, ImmutableBytesWritable, ImmutableBytesWritable, LongWritable> {
    private static LongWritable ZERO = new LongWritable(0);
    public static enum Counters {
      SHUFFLED_ROWS
    } 
    public void reduce(LongWritable key, Iterable<ImmutableBytesWritable> values, Context context)
        throws IOException, InterruptedException {

      for (ImmutableBytesWritable k : values) {
        context.write(k, ZERO);
        context.getCounter(Counters.SHUFFLED_ROWS).increment(1);     
      }
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
    conf.set("ShuffleKeys.samplePercent", samplePercent);
    conf.set("ShuffleKeys.sampleCount", sampleCount);
    

    Job job = Job.getInstance(conf, "ShuffleKeys " + inputPath + " to " + outputPath);
    TableMapReduceUtil.addDependencyJars(job);
    job.setJarByClass(ShuffleKeys.class);
    
    Path outputDir = new Path(outputPath);
    if (outputDir.getFileSystem(getConf()).exists(outputDir)) {
      throw new IOException("Output directory " + outputDir + " already exists.");
    }
    FileOutputFormat.setOutputPath(job, outputDir);
    
    Path inputDir = new Path(inputPath);
    job.setMapperClass(ShuffleKeysMapper.class);
    job.setMapOutputKeyClass(LongWritable.class);
    job.setMapOutputValueClass(ImmutableBytesWritable.class);  
    SequenceFileInputFormat.addInputPath(job, inputDir);
    job.setInputFormatClass(SequenceFileInputFormat.class);    

    job.setReducerClass(ShuffleKeysReducer.class);
    job.setOutputFormatClass(SequenceFileOutputFormat.class);
    job.setOutputKeyClass(ImmutableBytesWritable.class);
    job.setOutputValueClass(LongWritable.class);

    
    return job.waitForCompletion(true) ? 0 : 1;

  }

  private void init() {

    options.addOption("o", "outputPath", true, "outputPath");
    options.addOption("i", "inputPath", true, "inputPath");  
    options.addOption("p", "samplePercent", true, "export a just a sample percentage instead of all rows.");
    options.addOption("r", "sampleCount", true, "export a just a sample record every few records instead of all rows.");    

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

    if (cmd.hasOption("i")) {
      inputPath = cmd.getOptionValue("i");
    }
    
    if (cmd.hasOption("p")) {
      samplePercent = cmd.getOptionValue("p");
    } 

    if (cmd.hasOption("r")) {
      sampleCount = cmd.getOptionValue("r");
    } 

    return true;
  }


  public static void main(String[] args) throws Exception {
    int exitCode = ToolRunner.run(new ShuffleKeys(), args);
    System.exit(exitCode);
  }
}
