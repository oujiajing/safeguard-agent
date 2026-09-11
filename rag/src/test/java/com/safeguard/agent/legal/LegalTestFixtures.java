package com.safeguard.agent.legal;

import com.safeguard.agent.infra.token.HeuristicTokenCounterService;
import com.safeguard.agent.legal.chunk.LegalChunker;
import com.safeguard.agent.legal.clean.EmptyElementCleanupStep;
import com.safeguard.agent.legal.clean.LegalCleaningPipeline;
import com.safeguard.agent.legal.clean.LegalNumberWhitespaceNormalizationStep;
import com.safeguard.agent.legal.clean.UnicodeNormalizationStep;
import com.safeguard.agent.legal.clean.WhitespaceNormalizationStep;
import com.safeguard.agent.legal.config.LegalIngestionProperties;
import com.safeguard.agent.legal.ingest.CleanedTextImporter;
import com.safeguard.agent.legal.metadata.LegalMetadataExtractor;
import com.safeguard.agent.legal.parser.DefaultLegalStructureParser;
import com.safeguard.agent.legal.qc.LegalQualityService;

final class LegalTestFixtures {

    private LegalTestFixtures() {
    }

    static LegalIngestionProperties properties() {
        return new LegalIngestionProperties();
    }

    static CleanedTextImporter importer() {
        return importer(properties());
    }

    static CleanedTextImporter importer(LegalIngestionProperties properties) {
        LegalCleaningPipeline cleaning = new LegalCleaningPipeline(java.util.List.of(
                new UnicodeNormalizationStep(),
                new WhitespaceNormalizationStep(),
                new LegalNumberWhitespaceNormalizationStep(),
                new EmptyElementCleanupStep()));
        return new CleanedTextImporter(
                cleaning,
                new LegalMetadataExtractor(),
                new DefaultLegalStructureParser(),
                new LegalChunker(new HeuristicTokenCounterService(), properties),
                new LegalQualityService(properties));
    }
}
