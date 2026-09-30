package com.fx.lab.core.generator;

import com.fx.lab.core.model.MarketDataUpdate;
import com.fx.lab.core.recording.BinaryStreamRecorder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility main to generate reproducible sample binary data files for offline replay demonstrations.
 */
public class SampleDataGeneratorMain {
    public static void main(String[] args) throws IOException {
        Path outPath = Paths.get("sample-data", "sample_overload_stream.bin");
        Path serviceRecordingsPath = Paths.get("recordings", "sample_overload_stream.bin");

        GeneratorConfig config = GeneratorConfig.fromProfile(TrafficProfile.OVERLOAD);
        config.setRandomSeed(42L);
        config.setUpdatesPerSecond(50_000);
        config.setBurstSize(1000);

        MarketDataGenerator generator = new MarketDataGenerator(config);
        generateFile(generator, outPath, 2000);

        MarketDataGenerator generator2 = new MarketDataGenerator(config);
        generateFile(generator2, serviceRecordingsPath, 2000);

        System.out.println("Generated sample deterministic datasets at " + outPath + " and " + serviceRecordingsPath);
    }

    private static void generateFile(MarketDataGenerator generator, Path target, int count) throws IOException {
        try (BinaryStreamRecorder recorder = new BinaryStreamRecorder(target)) {
            MarketDataUpdate update = new MarketDataUpdate();
            for (int i = 0; i < count; i++) {
                generator.nextUpdate(update);
                recorder.record(update);
            }
        }
    }
}
