/**
 * Created on February 17, 2011
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
package org.dmpp.adf.logical

import org.dmpp.adf.util._
import org.dmpp.adf.physical._

/**
 * Symbolic constants for boot blocks.
 */
object BootBlock {
  val FlagFFS              = 1
  val FlagIntlOnly         = 2
  val FlagDirCacheAndIntl  = 4
}

/**
 * This class represents the boot block on an Amiga volume.
 * @constructor creates a boot block for the given physical volume
 * @param physicalVolume a [[org.dmpp.adf.physical.PhysicalVolume]] instance.
 */
class BootBlock(physicalVolume: PhysicalVolume) extends HasChecksum with BitHelper {
  import BootBlock._

  /**
   * Initializes an empty boot block.
   */
  def initialize {
    physicalVolume(0) = 'D'
    physicalVolume(1) = 'O'
    physicalVolume(2) = 'S'
  }

  def filesystemType  = if (flagClear(flags, FlagFFS)) "OFS" else "FFS"
  def isInternational = flagSet(flags, FlagIntlOnly) ||
  flagSet(flags, FlagDirCacheAndIntl)
  def useDirCache     = flagSet(flags, FlagDirCacheAndIntl)
  private def flags = physicalVolume(3) & 0x07
  
  def rootBlockNumber = physicalVolume.int32At(8)
  def storedChecksum  = physicalVolume.int32At(4)
  def recomputeChecksum = physicalVolume.setInt32At(4, computedChecksum)
  
  def computedChecksum: Int = {
    import UnsignedInt32Conversions._

    var sum: UnsignedInt32 = 0
    for (i <- 0 until 1024 by 4) {
      if (i != 4) {
        sum += (physicalVolume.int32At(i) & 0xffffffffl)
        if (sum.overflowOccurred) sum += 1
      }
    }
    ~sum.intValue
  }
}

