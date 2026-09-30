package com.fx.lab.core.recording;

import com.fx.lab.core.model.MarketDataUpdate;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultra-fast binary stream recorder using direct memory buffers and FileChannel.
 */
public final class BinaryStreamRecorder implements AutoCloseable {
    private static final int BUFFER_CAPACITY = 64 * 1024; // 64KB buffer

    private final Path filePath;
    private final FileChannel fileChannel;
    private final ByteBuffer directBuffer;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicLong recordedCount = new AtomicLong(0);

    public BinaryStreamRecorder(Path filePath) throws IOException {
        this.filePath = filePath;
        // Ensure parent directories exist
        if (filePath.getParent() != null) {
            java.nio.file.Files.createDirectories(filePath.getParent());
        }
        this.fileChannel = FileChannel.open(filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        this.directBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        this.recording.set(true);
    }

    public synchronized void record(MarketDataUpdate update) {
        if (!recording.get()) return;

        if (directBuffer.remaining() < MarketDataUpdate.BINARY_FRAME_SIZE) {
            flushBuffer();
        }

        update.writeTo(directBuffer);
        recordedCount.incrementAndGet();
    }

    private void flushBuffer() {
        try {
            directBuffer.flip();
            while (directBuffer.hasRemaining()) {
                fileChannel.write(directBuffer);
            }
            directBuffer.clear();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to flush binary recorder buffer", e);
        }
    }

    public synchronized void stop() {
        if (recording.compareAndSet(true, false)) {
            flushBuffer();
            try {
                fileChannel.force(true);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        stop();
        if (fileChannel.isOpen()) {
            fileChannel.close();
        }
    }

    public boolean isRecording() {
        return recording.get();
    }

    public long getRecordedCount() {
        return recordedCount.get();
    }

    public Path getFilePath() {
        return filePath;
    }
}
