package com.safeguard.agent.infra.rerank;

import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.infra.enums.ModelProvider;
import com.safeguard.agent.infra.model.ModelTarget;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class NoopRerankClient implements RerankClient {

    @Override
    public String provider() {
        return ModelProvider.NOOP.getId();
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> queryTerms = terms(query);
        List<RetrievedChunk> ranked = candidates.stream()
                .map(chunk -> chunk.toBuilder().rerankScore(heuristicScore(queryTerms, chunk)).build())
                .sorted((a, b) -> Float.compare(b.getRerankScore(), a.getRerankScore()))
                .toList();
        return topN <= 0 ? ranked : ranked.stream().limit(topN).collect(Collectors.toList());
    }

    private float heuristicScore(Set<String> queryTerms, RetrievedChunk chunk) {
        if (queryTerms.isEmpty() || chunk.getText() == null) return 0.05f;
        String text = chunk.getText().toLowerCase(Locale.ROOT);
        long matched = queryTerms.stream().filter(text::contains).count();
        float overlap = (float) matched / queryTerms.size();
        float prior = chunk.getScore() == null || !Float.isFinite(chunk.getScore()) ? 0f
                : Math.max(0f, Math.min(1f, chunk.getScore()));
        return Math.min(1f, overlap * 0.85f + prior * 0.15f);
    }

    private Set<String> terms(String query) {
        if (query == null || query.isBlank()) return Set.of();
        String normalized = query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        Set<String> words = Stream.of(normalized.split("\\s+"))
                .filter(word -> word.length() >= 2).collect(Collectors.toSet());
        for (int i = 0; i + 1 < normalized.length(); i++) {
            char a = normalized.charAt(i), b = normalized.charAt(i + 1);
            if (Character.isLetterOrDigit(a) && Character.isLetterOrDigit(b)) {
                words.add(normalized.substring(i, i + 2));
            }
        }
        return words;
    }
}
