package org.dmpp.adf

import java.io._
import java.nio._

object AdfFile {
  val BytesPerSector       = 512
  val NumSectorsPerTrack   = 11
  val NumTracksPerCylinder = 2
  val NumCylindersPerDisk  = 80
  val FileSize = BytesPerSector * NumSectorsPerTrack *
                 NumTracksPerCylinder * NumCylindersPerDisk 
}

class Sector(data: Array[Byte], offset: Int) extends BitHelper {
  import AdfFile._

  def apply(byteNum: Int) = {
    if (byteNum >= BytesPerSector) throw new IndexOutOfBoundsException("invalid byte num")
    data(offset + byteNum) & 0xff
  }
  def update(byteNum: Int, value: Int) {
    if (byteNum >= BytesPerSector) throw new IndexOutOfBoundsException("invalid byte num")
    data(offset + byteNum) = (value & 0xff).asInstanceOf[Byte]
  }
  def longAt(byteNum: Int) = {
    makeLong(data(offset + byteNum),     data(offset + byteNum + 1),
             data(offset + byteNum + 2), data(offset + byteNum + 3))
  }
}

trait PhysicalVolume {
  def sector(sectorNum: Int): Sector
  def bytesPerSector: Int
}

class AdfFile(data: Array[Byte]) extends PhysicalVolume {
  import AdfFile._

  def sector(sectorNum: Int) = new Sector(data, sectorNum * BytesPerSector)
  def bytesPerSector = AdfFile.BytesPerSector
}

object AdfFileFactory {
  def readAdfFile(input: InputStream) = {
    val data = new Array[Byte](AdfFile.FileSize)
    input.read(data)
    new AdfFile(data)
  }
}
