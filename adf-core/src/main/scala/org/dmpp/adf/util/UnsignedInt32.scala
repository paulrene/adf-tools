/**
 * Created on February 26, 2011
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
