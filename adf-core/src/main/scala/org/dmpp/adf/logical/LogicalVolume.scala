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
package org.dmpp.adf.logical

import java.util.Date
import org.dmpp.adf.util._
import org.dmpp.adf.physical._
import java.io._

class DeviceIsFull extends Exception
class DirectoryEntryNotFound extends Exception

/**
 * A factory to create logical volume instances.
 */
object LogicalVolumeFactory {
  /**
   * Creates an empty, non-bootable DD "formatted" disk.
   * @param volume name (optional)
   * @return initialized [[org.dmpp.adf.logical.LogicalVolume]] instance
   */
  def createEmptyDoubleDensityDisk(name: String = "Empty",
                                   filesystemType: String = "FFS") = {
    val logicalVolume =
      new LogicalVolume(PhysicalVolumeFactory.createEmptyDoubleDensityDisk)
    logicalVolume.initialize(name, filesystemType)
    logicalVolume
  }
}

/**
 * Symbolic constants for logical volumes.
 */
object LogicalVolume {
  val RootSectorNumber   = 880
}

/**
 * A logical volume based on an underlying physical volume. On this
 * layer, we talk about blocks, which are effectively typed sectors.
 *
 * @constructor creates a logical volume instance with a physical volume
 * @param physicalVolume the physical volume the logical volume is based on
 */
class LogicalVolume(val physicalVolume: PhysicalVolume) {
  import LogicalVolume._

  /**
   * Initializes this volume as an empty volume.
   * @param name volume name
   * @param filesystemType file system type, defaults to "FFS"
   */
  def initialize(name: String,
                 filesystemType: String = "FFS") {
    bootBlock.initialize(filesystemType)
    rootBlock.initialize(name)
    new BitmapBlock(this, 881).initialize
    allocate(880) // root block
    allocate(881) // bitmap block
  }

  /**
   * This volume's file system type. "OFS" for Original File System, "FFS" for
   * Fast File System.
   * @return the file system type
   */
  def filesystemType = bootBlock.filesystemType

  /**
   * Writes the contents of this volume to the specified [[java.io.OutputStream]].
   * @param out the OutputStream
   */
  def writeToOutputStream(out: OutputStream) = physicalVolume.writeToOutputStream(out)

  /**
   * This volume's size in bytes.
   * @return size in bytes
   */
  def sizeInBytes = physicalVolume.sizeInBytes

  /**
   * This volume's block size in bytes.
   * @return block size in bytes
   */
  def blockSizeInBytes = physicalVolume.bytesPerSector

  /**
   * Total number of blocks.
   * @return total number of blocks
   */
  def numBlocksTotal = sizeInBytes / blockSizeInBytes

  /**
   * Reads the byte at byteNum.
   * @param byteNum the byte number
   * @return the byte value at the specified position
   */
  def apply(byteNum: Int) = physicalVolume(byteNum)

  /**
   * Reads the 32 bit int at byteNum.
   * @param byteNum the byte number
   * @return int32 value
   */
  def int32At(byteNum: Int) = physicalVolume.int32At(byteNum)

  /** This volume's boot block. */
  val bootBlock = new BootBlock(this)

  /** This volumes's root block. */
  val rootBlock = new RootBlock(this, RootSectorNumber)

  /**
   * Retrieves the UserDirectory block with the specified block number.
   * @param blockNumber the block number
   * @return the UserDirectoryBlock
   */
  def userDirectoryBlockAt(blockNumber: Int): UserDirectoryBlock = {
    new UserDirectoryBlock(this, blockNumber)
  }

  /**
   * Retrieves the DirectoryBlock with the specified block number.
   * @param blockNumber the block number
   * @return the directory block
   */
  def directoryBlockAt(blockNumber: Int): DirectoryBlock = {
    if (blockNumber == rootBlock.blockNumber) rootBlock
    else userDirectoryBlockAt(blockNumber)
  }

  /**
   * Returns this volume's name.
   * @return the volume's name
   */
  def name = rootBlock.name

  /**
   * Marks the specified block as used. This method directly allocates a
   * block number in the system. It is recommended to use
   * allocate() instead in order to let the volume automatically find the
   * next available block.
   * @param blockNumber the block number to mark as used
   */
  def allocate(blockNumber: Int) {
    // currently, we only support one bitmap block - no hard drives and
    // HD disks
    val bitmapBlock0 = rootBlock.bitmapBlockAt(0).get
    bitmapBlock0.allocate(blockNumber - 2)
  }

  /**
   * Determines whether the specified block is marked as allocated on this volume.
   * @param blockNumber the block number
   * @return true if allocated, false if free
   */
  def isAllocated(blockNumber: Int) = {
    rootBlock.bitmapBlockAt(0).get.isAllocated(blockNumber - 2)
  }

  /**
   * Retrieves the next free block on this volume. The allocation follows AmigaDOS:
   * It starts looking at all blocks > 880 and if it can't find one, it
   * searches for blocks > 1. If there is no free block to be found, DeviceIsFull
   * is thrown.
   * @return next free block number
   */
  def allocate: Int = {
    // only one bitmap block is currently used, so we can use a simple, but
    // slow algorithm.
    val bitmapBlock0 = rootBlock.bitmapBlockAt(0).get
    val freeBlocks = freeBlockNumbers
    if (freeBlocks.length == 0) throw new DeviceIsFull
    val freeBlocksGreater880 = freeBlocks.filter(i => i > 880)
    val blockNumber = if (freeBlocksGreater880.length > 0) freeBlocksGreater880.head
                        else freeBlocks.head
    allocate(blockNumber)
    blockNumber
  }

  /**
   * Returns a new, initialized file header block.
   * This method has a couple of global effects:
   * - the block number used will be marked as used in the bitmap
   * - the parent's hash table will contain the entry
   * - the root block and parent's modification times are updated
   * - checksums of modified blocks are updated
   * 
   * @param parentBlock block number of parent
   * @param name file name
   * @param numDataBlocks number of data blocks
   * @return a new, initialized FileHeaderBlock
   */
  def createFileHeaderBlockIn(parentBlock: DirectoryBlock,
                              name: String): FileHeaderBlock = {
    val fileheader = new FileHeaderBlock(this, allocate)
    fileheader.initialize(parentBlock.blockNumber, name)
    parentBlock.addToHashtable(fileheader)
    fileheader
  }

  /**
   * Allocates a new, initialized user directory block.
   * This method has a couple of global effects:
   * - the block number used will be marked as used in the bitmap
   * - the parent's hash table will contain the entry
   * - the root block and parent's modification times are updated
   * - checksums of modified blocks are updated
   * 
   * @param parentBlock the parent block's number
   * @param name the directory name
   */
  def createUserDirectoryBlockIn(parentBlock: DirectoryBlock,
                                 name: String): UserDirectoryBlock = {
    val dirblock = new UserDirectoryBlock(this, allocate)
    dirblock.initialize(parentBlock.blockNumber, name)
    parentBlock.addToHashtable(dirblock)
    rootBlock.updateDiskLastModificationTime
    dirblock
  }

  /**
   * Renames a directory entry within the specified parent block.
   * @param parentBlock the parent directory block
   * @param oldName the old name of the entry
   * @param newName the new name of the entry
   */
  def renameDirectoryEntryIn(parentBlock: DirectoryBlock,
                             oldName: String, newName: String) = {
    parentBlock.blockForName(oldName) match {
      case Some(entry) =>
        parentBlock.removeFromHashtable(entry.name)
        entry.name = newName
        parentBlock.addToHashtable(entry)
        entry
      case _  => throw new DirectoryEntryNotFound
    }
  }

  /**
   * Removes the directory entry with the specified name from its parent directory.
   * If the name does not exist, a DirectoryEntryNotFound is thrown.
   * When removing a FileHeaderBlock, the attached DataBlock instances are
   * also marked as freed from the bitmap.
   * When removing a UserDirectoryBlock, this method recursively removes all the
   * sub entries from the bitmap.
   * Note: The structure of the blocks is retained, so it is possible to restore
   * the disk's data by scanning the disk blocks and rebuilding the bitmap.
   * 
   * @param parentBlock the parent directory block
   * @param name the entry name
   */
  def removeDirectoryEntryFrom(parentBlock: DirectoryBlock,
                               name: String) {
    parentBlock.blockForName(name) match {
      case Some(entry) =>
        parentBlock.removeFromHashtable(name)
        freeBlock(entry.blockNumber)
        if (entry.isDirectory) {
          // remove sub entries
          val currentDir = entry.asInstanceOf[UserDirectoryBlock]
          val subEntries = currentDir.hashtableEntries
          for (subEntry <- subEntries) {
            removeDirectoryEntryFrom(currentDir, subEntry.name)
          }
        } else {
          // remove data blocks
          val dataBlockNumbers = entry.asInstanceOf[FileHeaderBlock].dataBlockNumbers
          dataBlockNumbers.foreach(freeBlock _)
        }
        rootBlock.recomputeChecksum
      case _ => throw new DirectoryEntryNotFound
    }
  }

  private def freeBlock(blockNumber: Int) {
    rootBlock.bitmapBlockAt(0).get.free(blockNumber - 2)
  }

  /**
   * Moves a DirectoryEntry from one DirectoryBlock to another.
   * @param directoryEntry the DirectoryEntry to move
   * @param destParentBlock the destination directory
   */
  def moveDirectoryEntryTo(directoryEntry: DirectoryEntryBlock,
                           destParentBlock: DirectoryBlock) {
    val srcParentBlock = directoryBlockAt(directoryEntry.parent)
    srcParentBlock.removeFromHashtable(directoryEntry.name)
    destParentBlock.addToHashtable(directoryEntry)
    rootBlock.updateDiskLastModificationTime
  }

  /**
   * Returns a new, initialized data block. The block used by this block will
   * be marked as used.
   * @param fileHeaderBlock the FileHeaderBlock this data block belongs to
   * @param seqnum the sequence number of this block, 1-based
   * @return a new, initialized DataBlock
   */
  def allocateDataBlock(fileHeaderBlock: FileHeaderBlock,
                        seqnum: Int, dataSize: Int): DataBlock = {
    if (seqnum <= 0) throw new IllegalArgumentException("seqnum must be > 0")
    val datablock = if (filesystemType == "OFS") {
      val ofsblock = new OfsDataBlock(this, allocate)
      ofsblock.initialize(fileHeaderBlock.blockNumber, seqnum, dataSize)
      ofsblock
    } else if (filesystemType == "FFS") {
      val ffsblock = new FfsDataBlock(this, allocate)
      ffsblock.initialize
      ffsblock
    } else {
      throw new UnsupportedOperationException("unknown file system")
    }
    datablock
  }

  /**
   * Returns the number of free blocks.
   * @return number of free blocks
   */
  def numFreeBlocks = freeBlockNumbers.length

  /**
   * Returns the number of used blocks.
   * @return number of used blocks
   */
  def numUsedBlocks = usedBlockNumbers.length

  private def freeBlockNumbers: List[Int] = {
    val bitmapBlock0 = rootBlock.bitmapBlockAt(0).get
    bitmapBlock0.freeBlockIndexes.filter(index =>
      index < physicalVolume.numSectorsTotal - 2).map(index => index + 2)
  }

  private def usedBlockNumbers: List[Int] = {
    val bitmapBlock0 = rootBlock.bitmapBlockAt(0).get
    bitmapBlock0.usedBlockIndexes.filter(index =>
      index < physicalVolume.numSectorsTotal - 2).map(index => index + 2)
  }

  /**
   * Returns the data block with the specified block number.
   * @param dataBlockNumber the data block number
   * @return the data block
   */
  def dataBlock(dataBlockNumber: Int) = {
    if (filesystemType == "OFS") new OfsDataBlock(this, dataBlockNumber)
    else if (filesystemType == "FFS") new FfsDataBlock(this, dataBlockNumber)
    else throw new UnsupportedOperationException("unknown file system type")
  }

  /**
   * Returns the number of bytes per data block. On FFS this is equal to the
   * entire block size, on OFS, we need to subtract the header size.
   * @return number of bytes available in a data block
   */
  def numBytesPerDataBlock: Int = {
    if (filesystemType == "FFS") blockSizeInBytes
    else if (filesystemType == "OFS") blockSizeInBytes - OfsDataBlock.HeaderSize
    else throw new UnsupportedOperationException("unknown file system type")
  }
}
