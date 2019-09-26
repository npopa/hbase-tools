package com.cloudera.ps.hbasestuff;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.io.Writable;


public class SampleWritable implements Writable {
    // Some data
    private long counter;
    private long size;

    // Default constructor to allow (de)serialization
    SampleWritable() { }
    
    public SampleWritable(long counter,long size) { set(counter, size); }
    public void set(long counter,long size) { this.counter = counter; this.size = size; }

    
    public long getCounter() {
      return counter;
    }

    public void setCounter(long counter) {
      this.counter = counter;
    }

    public long getSize() {
      return size;
    }

    public void setSize(long size) {
      this.size = size;
    }

    public void write(DataOutput out) throws IOException {
      out.writeLong(counter);
      out.writeLong(size);
    }

    public void readFields(DataInput in) throws IOException {
      counter = in.readLong();
      size = in.readLong();
    }

    public static SampleWritable read(DataInput in) throws IOException {
      SampleWritable w = new SampleWritable();
      w.readFields(in);
      return w;
    }
    
    public String toString() {
      return "["+Long.toString(counter)+", "+Long.toString(size)+"]";
    }
  }