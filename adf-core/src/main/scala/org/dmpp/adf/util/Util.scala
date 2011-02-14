/**
 * Created on February 12, 2011
 * Copyright (c) 2011, Wei-ju Wu
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *   * Neither the name of Wei-ju Wu nor the
 *     names of its contributors may be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY WEI-JU WU ''AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL WEI-JU WU BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.dmpp.adf.util

import java.text.SimpleDateFormat
import java.util.Date

/**
 * Mixin for common bit operations.
 */
trait BitHelper {
  /**
   * Determines whether the specified flags are cleared.
   * 
   * @param flags the value to check for cleared flags
   * @param flag a bit mask with the flags to be checked set
   */
  def flagClear(flags: Int, flag: Int) = (flags & flag) == 0

  /**
   * Determines whether the specified flags are set.
   * 
   * @param flags the value to check for set flags
   * @param flag a bit mask with the flags to be checked set
   */
  def flagSet(flags: Int, flag: Int) = (flags & flag) == flag

  /**
   * Returns a list of bit numbers that are set in a given bit mask.
   * Note that the MSB in this case is number 0 and the LSB
   * is 31.
   *
   * @param bitmask the bit mask to check
   * @return list of bit numbers that are set
   */
  def bitsSetIn(bitmask: Int): List[Int] = {
    bitsFilter(bitmask, mask => ((mask & 1) == 1))
  }

  /**
   * Returns a list of bit numbers that are set in a given bit mask.
   * Note that the MSB in this case is number 0 and the LSB
   * is 31.
   *
   * @param bitmask the bit mask to check
   * @return list of bit numbers that are set
   */
  def bitsClearIn(bitmask: Int): List[Int] = {
    bitsFilter(bitmask, mask => ((mask & 1) == 0))
  }

  private def bitsFilter(bitmask: Int, pred: Int => Boolean): List[Int] = {
    var current = bitmask
    var result: List[Int] = Nil
    for (i <- 0 until 32) {
      if (pred(current)) result ::= (31 - i)
      current >>>= 1
    }
    result
  }

  /**
   * Given four byte values (from most significant to least significant),
   * join them into a 32-bit integer value.
   */
  def makeInt32(byte0: Int, byte1: Int, byte2: Int, byte3: Int) = {
    ((byte0 << 24) & 0xff000000) | ((byte1 << 16) & 0xff0000) |
      ((byte2 << 8) & 0xff00) | (byte3 & 0xff)
  }
}

/**
 * Implicit conversion in order to use the UnsignedInt class more
 * naturally.
 */
object UnsignedInt32Conversions {
  implicit def uint2Long(uint: UnsignedInt32): Long = uint.value
  implicit def uint2Int(uint: UnsignedInt32): Int = uint.intValue
  implicit def long2Uint(value: Long): UnsignedInt32 = UnsignedInt32(value)
  implicit def int2Uint(value: Int): UnsignedInt32 = {
    UnsignedInt32(value.asInstanceOf[Long])
  }
}

/**
 * Constants for UnsignedInt32 class.
 */
object UnsignedInt32 {
  val MaxValue = 4294967295l
}

/**
 * A class to emulate unsigned int behavior for addition, namely wrapping around
 * when an addition overflow occurs. In that case, the "overflowOccurred" flag
 * is also set on the resulting value.
 * 
 * @constructor creates a new UnsignedInt32 value with optional overflow flag
 * @param value the represented value
 * @param overflowOccurred flag to indicate whether an overflow occurred during
 *        an arithmetic operation
 */
case class UnsignedInt32(value: Long, overflowOccurred: Boolean = false) {
  import UnsignedInt32._

  if (value > MaxValue) {
    throw new IllegalArgumentException("value exceeds maximal unsigned 32 bit range")
  }
  if (value < 0) {
    throw new IllegalArgumentException("attempted to create UnsignedInt with a" +
                                       "negative value")
  }

  /**
   * Addition of UnsignedInt32 values. If a 32-bit overflow occurs during the addition,
   * the result value's overflowOccurred flag will be set to true.
   *
   * @param aValue value that is added to this object
   * @return a new UnsignedInt representing the result
   */
  def +(aValue: UnsignedInt32): UnsignedInt32 = {
    import scala.math._
    val result = (value + aValue.value) % (MaxValue + 1)
    val overflowOccurred = result < max(value, aValue.value)
    UnsignedInt32(result, overflowOccurred)
  }

  /**
   * A Scala Int value representing this value. It should be pointed out that
   * while UnsignedInt32 values are never negative, the resulting Int is
   * possibly negative.
   *
   * @return the correponding Scala Int value
   */
  def intValue: Int = value.asInstanceOf[Int]
}

/**
 * Constant definitions for Amiga DOS dates.
 */
object AmigaDosDate {
  val DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
  val MillisecondsPerTick      = 20
  val MillisecondsPerMinute    = 1000 * 60
  val MillisecondsPerDay: Long = 1000 * 60 * 60 * 24
}

/**
 * AmigaDOS dates start at January 1st, 1978, rather than Java dates, which
 * start at January 1st, 1970.
 * Ticks are measured in 1/50 of a second.
 *
 * @constructor creates an AmigaDOS date value with the specified arguments
 * @param daysSinceJan_1_78 days since January 1st, 1978
 * @param minutesPastMidnight minutes past 00:00
 * @param ticksPastLastMinute ticks elapsed since last minute
 */
case class AmigaDosDate(daysSinceJan_1_78: Int, minutesPastMidnight: Int,
                        ticksPastLastMinute: Int) {
  import AmigaDosDate._

  /**
   * Returns the Java Date equivalent of this AmigaDOS date
   * @return a java.util.Date representing this AmigaDOS date
   */
  def toDate: Date = {
    val baseMillis = DateFormat.parse("1978-01-01 00:00:00.000").getTime
    new Date(baseMillis +
             daysToMillis(daysSinceJan_1_78) +
             minutesToMillis(minutesPastMidnight) +
             ticksToMillis(ticksPastLastMinute))
  }

  private def daysToMillis(days: Int)       = days * MillisecondsPerDay
  private def minutesToMillis(minutes: Int) = minutes * MillisecondsPerMinute
  private def ticksToMillis(ticks: Int)     = ticks * MillisecondsPerTick

  /**
   * String representation of this date.
   * @return string representation
   */
  override def toString = DateFormat.format(toDate)
}
