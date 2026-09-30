package com.fx.lab.core.recording;

import com.fx.lab.core.model.MarketDataUpdate;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Replays deterministically recorded binary streams.
 */
public final class BinaryStreamReplayer {
    private static final int BUFFER_CAPACITY = 64 * 1024;

    private final Path filePath;

    public BinaryStreamReplayer(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Read all recorded updates into memory for fast deterministic offline replay.
     */
    public List<MarketDataUpdate> loadAll() throws IOException {
        List<MarketDataUpdate> updates = new ArrayList<>();
        stream(updates::add);
        return updates;
    }

    /**
     * Stream through all recorded updates from disk.
     */
    public void stream(Consumer<MarketDataUpdate> consumer) throws IOException {
        try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
            buffer.limit(0);

            byte[] frameBytes = new byte[MarketDataUpdate.BINARY_FRAME_SIZE];
            ByteBuffer frameBuffer = ByteBuffer.wrap(frameBytes);

            while (true) {
                if (buffer.remaining() < MarketDataUpdate.BINARY_FRAME_SIZE) {
                    buffer.compact();
                    int read = fileChannel.read(buffer);
                    buffer.flip();
                    if (read == -1 && buffer.remaining() < MarketDataUpdate.BINARY_FRAME_SIZE) {
                        break;
                    }
                }

                if (buffer.remaining() >= MarketDataUpdate.BINARY_FRAME_SIZE) {
                    buffer.get(frameBytes);
                    frameBuffer.clear();
                    MarketDataUpdate update = MarketDataUpdate.readFrom(frameBuffer);
                    consumer.accept(update);
                }
            }
        }
    }

    public Path getFilePath() {
        return filePath;
    }
}
