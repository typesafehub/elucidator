/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

object BasicTypes {
  /**
   * Point in time, represented as milliseconds since midnight, January 1, 1970 UTC.
   */
  type Timestamp = Long

  /**
   * Point in time, represented as nanoseconds since midnight, January 1, 1970 UTC.
   */
  type NanoTime = Long

  /**
   * Duration in nanoseconds.
   */
  type DurationNanos = Long

  /**
   * Duration in microseconds.
   */
  type DurationMicros = Long

  /**
   * Messages per second.
   */
  type Rate = Double

  /**
   * Bytes per second.
   */
  type BytesRate = Double

  /**
   * Number of bytes.
   */
  type Bytes = Long

  def convertBytes(
    bytes: Bytes,
    convertToKiloBytes: Boolean = false,
    convertToMegaBytes: Boolean = false): Double = {

    if (convertToKiloBytes)
      bytes / 1024.0
    else if (convertToMegaBytes)
      bytes / (1024.0 * 1024.0)
    else
      bytes
  }
}
