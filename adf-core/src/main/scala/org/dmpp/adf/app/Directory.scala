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
package org.dmpp.adf.app

import org.dmpp.adf.logical._

/**
 * Generic interface for directories.
 */
trait Directory extends DosFile {
  def isDirectory = true
  def isFile      = false

  /**
   * Lists all files in this directory.
   * @return the list of all files in this directory
   */
  def list: List[DosFile]

  /**
   * List only the sub directories in this directory.
   * @return the list of sub directories
   */
  def listDirectories: List[DosFile]

  /**
   * Try to retrieve a file with the specified name in this directory.
   * Returns none if not found, or Some(file) if it exists.
   * @param filename the name of the requested file
   * @return None or Some(file)
   */
  def find(filename: String): Option[DosFile]

  /**
   * Creates a new file from an array of bytes. If there is not enough space
   * on the volume, a DeviceIsFull exception is thrown.
   * @param filename the file name
   * @param dataBytes the array of data bytes to write
   */
  def createFile(filename: String, dataBytes: Array[Byte]): UserFile
}

trait ContainsHashtableBlock {
  def thisDirectoryBlock: DirectoryBlock
  def blockNumber: Int
  def hashtableEntries: List[DirectoryEntryBlock]
  def logicalVolume: LogicalVolume

  def list: List[DosFile] = {
    hashtableEntries.map(e => e match {
      case file:FileHeaderBlock   => new UserFile(logicalVolume, file)
      case dir:UserDirectoryBlock => new UserDirectory(logicalVolume, dir)
      case unknown:Any =>
        throw new IllegalArgumentException("unknown block type: " + unknown.getClass)
    })
  }
  def listDirectories: List[DosFile] = {
    list.filter(file => file.isDirectory)
  }

  def find(filename: String): Option[DosFile] = {
    val matchEntries = hashtableEntries.filter(e => e.name == filename)
    if (matchEntries.length > 0)
      Some(createFileOrDirectory(matchEntries(0))) 
    else None
  }

  private def createFileOrDirectory(directoryEntryBlock: DirectoryEntryBlock) = {
    directoryEntryBlock match {
      case dir:UserDirectoryBlock => new UserDirectory(logicalVolume, dir)
      case file:FileHeaderBlock => new UserFile(logicalVolume, file)
      case _ => throw new IllegalArgumentException("unknowk block type")
    }
  }
  def createFile(filename: String, dataBytes: Array[Byte]) = {
    // Need 1 file header block
    // + enough data blocks to hold the data bytes
    val fileHeader = createFileHeaderForNewFile(filename, dataBytes.length)
    writeDataToBlocks(fileHeader, dataBytes)
    fileHeader.recomputeChecksum
    thisDirectoryBlock.recomputeChecksum
    new UserFile(logicalVolume, fileHeader)
  }

  private def writeDataToBlocks(fileHeader: FileHeaderBlock, dataBytes: Array[Byte]) {
    val dataBlocks = allocateDataBlocks(fileHeader.headerKey, dataBytes.length)
    var srcPos = 0
    if (dataBlocks.length > 0) {
      fileHeader.firstDataBlockNumber = dataBlocks(0).blockNumber
      for (i <- 0 until dataBlocks.length) {
        val dataBlock = dataBlocks(i)
        srcPos = fillDataBlock(dataBlock, dataBytes, srcPos)

        fileHeader.setDataBlock(i, dataBlock.blockNumber)
        if (logicalVolume.filesystemType == "OFS") {
          val ofsBlock = dataBlock.asInstanceOf[OfsDataBlock]
          if (i < dataBlocks.length - 1) {
            ofsBlock.nextDataBlock = dataBlocks(i + 1).blockNumber
          }
          ofsBlock.recomputeChecksum
        }
      }
    }
  }

  private def fillDataBlock(dataBlock: DataBlock, dataBytes: Array[Byte],
                            srcIndex: Int) = {
    var srcPos = srcIndex
    var destPos = 0
    while (srcPos < dataBytes.length && destPos < dataBlock.maxDataBytes) {
      dataBlock(destPos) = dataBytes(srcPos)
      destPos += 1
      srcPos += 1
    }
    srcPos
  }
  private def createFileHeaderForNewFile(filename: String, fileSize: Int) = {
    val numRequiredDataBlocks = fileSize / logicalVolume.numBytesPerDataBlock
    if (numRequiredDataBlocks > logicalVolume.numFreeBlocks) {
      throw new DeviceIsFull
    }
    val fileHeader = logicalVolume.allocateFileHeaderBlock(blockNumber, filename)
    thisDirectoryBlock.addToHashtable(fileHeader)
    fileHeader.blockCount = numRequiredDataBlocks
    fileHeader.fileSize = fileSize
    fileHeader.updateLastAccessTime
    fileHeader
  }

  private def allocateDataBlocks(fileHeaderNum: Int, dataSize: Int): List[DataBlock] = {
    var numRequiredDataBlocks = dataSize / logicalVolume.numBytesPerDataBlock
    if ((dataSize % logicalVolume.numBytesPerDataBlock) > 0) numRequiredDataBlocks += 1
    printf("# allocated data blocks for data size: %d => %d\n",
           dataSize, numRequiredDataBlocks)

    var dataBlocks: List[DataBlock] = Nil
    var remainSize = dataSize
    for (i <- 0 until numRequiredDataBlocks) {
      val dataSize = math.min(remainSize, logicalVolume.numBytesPerDataBlock)
      dataBlocks ::= logicalVolume.allocateDataBlock(fileHeaderNum,
                                                     i + 1, dataSize)
      remainSize -= dataSize
    }
    dataBlocks.reverse
  }
}

/**
 * A special directory class for the root directory, since it is not
 * based on a DirectoryEntryBlock.
 * @constructor creates a new RootDirectory instance
 * @param logicanVolume a LogicalVolume instance
 */
class RootDirectory(val logicalVolume: LogicalVolume)
extends Directory with ContainsHashtableBlock {
  private def rootBlock    = logicalVolume.rootBlock
  def thisDirectoryBlock   = rootBlock
  def blockNumber          = rootBlock.blockNumber
  def name                 = rootBlock.name
  def comment              = "(no comment)"
  def hashtableEntries     = rootBlock.hashtableEntries
  def lastModificationTime = rootBlock.lastModificationTime
  def lastAccessTime       = rootBlock.lastAccessTime
}

/**
 * Disk directory representation.
 * @constructor creates a UserDirectory instance
 * @param logicalVolume a LogicalVolume
 * @param directoryBlock the underlying directory block
 */
class UserDirectory(val logicalVolume: LogicalVolume,
                    directoryBlock: UserDirectoryBlock)
extends AbstractDosFile(directoryBlock)
with Directory with ContainsHashtableBlock {
  def thisDirectoryBlock = directoryBlock
  def blockNumber        = directoryBlock.blockNumber
  def hashtableEntries   = directoryBlock.hashtableEntries
}
