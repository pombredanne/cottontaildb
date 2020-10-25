package org.vitrivr.cottontail.storage.engine.hare.disk.wal

import org.vitrivr.cottontail.storage.engine.hare.PageId
import org.vitrivr.cottontail.storage.engine.hare.disk.DiskManager
import org.vitrivr.cottontail.storage.engine.hare.disk.FileUtilities
import org.vitrivr.cottontail.storage.engine.hare.disk.structures.DataPage
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32C

/**
 * A file used for write-ahead logging. It allows for all the basic operations supported by
 * [DiskManager]s. The series of operations executed by this [WriteAheadLog] can then be replayed.
 *
 * @see WALDiskManager
 *
 * @author Ralph Gasser
 * @version 1.1.0
 */
open class WriteAheadLog(val manager: WALDiskManager, val lockTimeout: Long = 5000L) : AutoCloseable {

    companion object {
        /** Size of the [WALHeader] in bytes. */
        private const val WAL_HEADER_SIZE = 128

        /** Offset into the [WriteAheadLog] file to access the [WALHeader]. */
        private const val OFFSET_WAL_HEADER = 0L

        /**
         * Creates a new [WriteAheadLog] and opens and returns it.
         *
         * @param manager [WALDiskManager] to create the [WriteAheadLog] for.
         */
        fun create(manager: WALDiskManager): WriteAheadLog {
            /* Prepare header data for page file in the HARE format. */
            val walHeader = WALHeader().init()

            /** Write data to file and close. */
            val path = manager.path.parent.resolve(manager.path.fileName.toString() + ".wal")
            val channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.SYNC, StandardOpenOption.SPARSE)
            walHeader.write(channel, OFFSET_WAL_HEADER)
            channel.close()

            /* Open and return WriteAheadLog. */
            return WriteAheadLog(manager)
        }
    }

    /** The [Path] to the [WriteAheadLog] file. */
    private val path = this.manager.path.parent.resolve(this.manager.path.fileName.toString() + ".wal")

    /** [FileChannel] used to write to this [WriteAheadLog]*/
    private val fileChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SPARSE, StandardOpenOption.DSYNC)

    /** Acquire lock on [WriteAheadLog] file. */
    private val fileLock = FileUtilities.acquireFileLock(this.fileChannel, this.lockTimeout)

    /** Reference to the [WALHeader] of the HARE [WriteAheadLog] file. */
    private val walHeader = WALHeader().read(this.fileChannel, OFFSET_WAL_HEADER)

    /** Reference to the [WALEntry] reference used to access the entries in this [WriteAheadLog]. */
    private val entry = WALEntry()

    /** The [CRC32C] object used to calculate the CRC32 checksum of this [WriteAheadLog]. */
    private val crc32 = CRC32C()

    /** Number of [PageId]s that have been allocated in underlying [WALDiskManager] at time of creating this [WriteAheadLog]. */
    private var allocated = this.manager.pages

    /** An [ArrayDeque] of free [PageId]s held in underlying [WALDiskManager] at time of creating this [WriteAheadLog]. */
    private var freePageIds = ArrayDeque(this.manager.freePageIds)

    /**
     *
     */
    @Synchronized
    fun update(pageId: PageId, page: DataPage) {
        check(this.fileChannel.isOpen) { "HARE Write Ahead Log (WAL) has been closed and cannot be used for update() operation (file = ${this.path})." }
        require(pageId <= this.allocated) { "Error while logging HARE Write Ahead Log (WAL) update() operation, page ID $pageId is out of bounds (file = ${this.path})." }

        /* Prepare log entry. */
        this.entry.sequenceNumber = this.walHeader.entries++
        this.entry.action = WALAction.UPDATE
        this.entry.pageId = pageId
        this.entry.payloadSize = page.size

        /* Calculate CRC32 checksum. */
        this.crc32.update(this.entry.buffer.clear())
        this.crc32.update(page.buffer.clear())
        this.walHeader.checksum = this.crc32.value

        /* Write log entry. */
        this.entry.write(this.fileChannel, this.fileChannel.size())
        page.write(this.fileChannel, this.fileChannel.size())
        this.walHeader.write(this.fileChannel, OFFSET_WAL_HEADER)
    }

    /**
     * Allocates a new page and returns its [PageId]. Calling this method writes the action to the log.
     *
     * @return Allocated [PageId].
     */
    @Synchronized
    fun allocate(): PageId {
        check(this.fileChannel.isOpen) { "HARE Write Ahead Log (WAL) has been closed and cannot be used for allocate() operation (file = ${this.path})." }

        /* Prepare log entry. */
        if (this.freePageIds.size > 0) {
            this.entry.sequenceNumber = this.walHeader.entries++
            this.entry.action = WALAction.ALLOCATE_REUSE
            this.entry.pageId = this.freePageIds.removeLast()
            this.entry.payloadSize = 0
        } else {
            this.entry.sequenceNumber = this.walHeader.entries++
            this.entry.action = WALAction.ALLOCATE_APPEND
            this.entry.pageId = (++this.allocated)
            this.entry.payloadSize = 0
        }

        /* Calculate CRC32 checksum. */
        this.crc32.update(this.entry.buffer.clear())
        this.walHeader.checksum = this.crc32.value

        /* Write log entry. */
        this.entry.write(this.fileChannel, this.fileChannel.size())
        this.walHeader.write(this.fileChannel, OFFSET_WAL_HEADER)

        /* Returns the PageId. */
        return this.entry.pageId
    }

    /**
     * Frees the [PageId]
     *
     * @return Allocated [PageId].
     */
    @Synchronized
    fun free(pageId: PageId) {
        check(this.fileChannel.isOpen) { "HARE Write Ahead Log (WAL) has been closed and cannot be used for free() operation (file = ${this.path})." }
        require(pageId <= this.allocated) { "Error while logging HARE Write Ahead Log (WAL) free() operation, page ID $pageId is out of bounds (file = ${this.path})." }

        /* Prepare log entry. */
        if (pageId == this.allocated) {
            this.entry.sequenceNumber = this.walHeader.entries++
            this.entry.action = WALAction.FREE_TRUNCATE
            this.entry.pageId = pageId
            this.entry.payloadSize = 0
        } else if (pageId < this.allocated){
            this.freePageIds.addLast(pageId)
            this.entry.sequenceNumber = this.walHeader.entries++
            this.entry.action = WALAction.FREE_REUSE
            this.entry.pageId = pageId
            this.entry.payloadSize = 0
        }

        /* Calculate CRC32 checksum. */
        this.crc32.update(this.entry.buffer.clear())
        this.walHeader.checksum = this.crc32.value

        /* Write log entry. */
        this.entry.write(this.fileChannel, this.fileChannel.size())
        this.walHeader.write(this.fileChannel, OFFSET_WAL_HEADER)
    }

    /**
     * Replays this [WriteAheadLog] thus transferring all changes into the given destination.
     *
     * @param consumer A function that consumes the [WALEntry] entry and return true on success.
     */
    @Synchronized
    fun replay(consumer: (WALEntry, FileChannel) -> Boolean) {
        check(this.fileChannel.isOpen) { "HARE Write Ahead Log (WAL) file for {${this.path}} has been closed and cannot be used for replay." }

        /* Initialize start position for replay. */
        this.fileChannel.position(WALHeader.SIZE.toLong())
        for (seq in 0L until this.walHeader.entries) {
            this.entry.read(this.fileChannel)
            require(this.entry.sequenceNumber == seq) { "Error during HARE Write Ahead Log (WAL) replay(): Expected sequence number $seq does not match actual sequence number ${entry.sequenceNumber}." }

            /* Only process sequences numbers that have not been transferred already. */
            if (seq >= this.walHeader.transferred) {
                if (consumer(this.entry, this.fileChannel)) {
                    this.walHeader.transferred += 1
                    this.walHeader.write(this.fileChannel, OFFSET_WAL_HEADER)
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (this.fileChannel.isOpen) {
            this.fileLock.release()
            this.fileChannel.close()
        }
    }

    /**
     * Deletes this [WriteAheadLog] file. Calling this method also closes the associated [FileChannel].
     */
    @Synchronized
    fun delete() {
        this.close()
        Files.delete(this.path)
    }
}