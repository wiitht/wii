
package org.wiitht.wii.eel.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_IntervalCount extends IntervalCount {

  private final double rate;
  private final long count;

  AutoValue_IntervalCount(
      double rate,
      long count) {
    this.rate = rate;
    this.count = count;
  }

  @JsonProperty
  @Override
  public double rate() {
    return rate;
  }

  @JsonProperty
  @Override
  public long count() {
    return count;
  }

  @Override
  public String toString() {
    return "IntervalCount{"
         + "rate=" + rate + ", "
         + "count=" + count
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof IntervalCount) {
      IntervalCount that = (IntervalCount) o;
      return (Double.doubleToLongBits(this.rate) == Double.doubleToLongBits(that.rate()))
           && (this.count == that.count());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) ((Double.doubleToLongBits(this.rate) >>> 32) ^ Double.doubleToLongBits(this.rate));
    h *= 1000003;
    h ^= (int) ((this.count >>> 32) ^ this.count);
    return h;
  }

}
