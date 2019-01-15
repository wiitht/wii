
package org.wiitht.wii.eel.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_Snapshot extends Snapshot {

  private final long count;
  private final double throughput;
  private final double concurrency;
  private final double latency;
  private final double p50;
  private final double p90;
  private final double p99;
  private final double p999;
  private final double apdex;

  AutoValue_Snapshot(
      long count,
      double throughput,
      double concurrency,
      double latency,
      double p50,
      double p90,
      double p99,
      double p999,
      double apdex) {
    this.count = count;
    this.throughput = throughput;
    this.concurrency = concurrency;
    this.latency = latency;
    this.p50 = p50;
    this.p90 = p90;
    this.p99 = p99;
    this.p999 = p999;
    this.apdex = apdex;
  }

  @JsonProperty
  @Override
  public long count() {
    return count;
  }

  @JsonProperty
  @Override
  public double throughput() {
    return throughput;
  }

  @JsonProperty
  @Override
  public double concurrency() {
    return concurrency;
  }

  @JsonProperty
  @Override
  public double latency() {
    return latency;
  }

  @JsonProperty
  @Override
  public double p50() {
    return p50;
  }

  @JsonProperty
  @Override
  public double p90() {
    return p90;
  }

  @JsonProperty
  @Override
  public double p99() {
    return p99;
  }

  @JsonProperty
  @Override
  public double p999() {
    return p999;
  }

  @JsonProperty
  @Override
  public double apdex() {
    return apdex;
  }

  @Override
  public String toString() {
    return "Snapshot{"
         + "count=" + count + ", "
         + "throughput=" + throughput + ", "
         + "concurrency=" + concurrency + ", "
         + "latency=" + latency + ", "
         + "p50=" + p50 + ", "
         + "p90=" + p90 + ", "
         + "p99=" + p99 + ", "
         + "p999=" + p999 + ", "
         + "apdex=" + apdex
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Snapshot) {
      Snapshot that = (Snapshot) o;
      return (this.count == that.count())
           && (Double.doubleToLongBits(this.throughput) == Double.doubleToLongBits(that.throughput()))
           && (Double.doubleToLongBits(this.concurrency) == Double.doubleToLongBits(that.concurrency()))
           && (Double.doubleToLongBits(this.latency) == Double.doubleToLongBits(that.latency()))
           && (Double.doubleToLongBits(this.p50) == Double.doubleToLongBits(that.p50()))
           && (Double.doubleToLongBits(this.p90) == Double.doubleToLongBits(that.p90()))
           && (Double.doubleToLongBits(this.p99) == Double.doubleToLongBits(that.p99()))
           && (Double.doubleToLongBits(this.p999) == Double.doubleToLongBits(that.p999()))
           && (Double.doubleToLongBits(this.apdex) == Double.doubleToLongBits(that.apdex()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) ((this.count >>> 32) ^ this.count);
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.throughput) >>> 32) ^ Double.doubleToLongBits(this.throughput));
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.concurrency) >>> 32) ^ Double.doubleToLongBits(this.concurrency));
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.latency) >>> 32) ^ Double.doubleToLongBits(this.latency));
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.p50) >>> 32) ^ Double.doubleToLongBits(this.p50));
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.p90) >>> 32) ^ Double.doubleToLongBits(this.p90));
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.p99) >>> 32) ^ Double.doubleToLongBits(this.p99));
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.p999) >>> 32) ^ Double.doubleToLongBits(this.p999));
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.apdex) >>> 32) ^ Double.doubleToLongBits(this.apdex));
    return h;
  }

}
