package com.safeguard.agent.rag.core.retrieval.postprocessor;

import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.rag.core.retrieval.channel.SearchChannelResult;
import com.safeguard.agent.rag.core.retrieval.channel.SearchContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps one strong lexical candidate for explicit multi-part safety questions when a semantic
 * reranker over-focuses on one half of the question. It only activates for compound markers and
 * requires two domain anchors in the candidate text, limiting same-token hard-negative leakage.
 */
@Component
public class LexicalCoveragePostProcessor implements SearchResultPostProcessor {
    private static final List<String> ANCHORS = List.of(
            "安全帽", "安全带", "临边", "洞口", "防护栏杆", "脚手架", "模板支撑", "起重吊装",
            "起吊", "架空线路", "临时用电", "应急预案", "拆除工程", "安全生产许可证",
            "高处作业", "雨雪", "防滑", "密闭空间", "通风", "有害物");

    @Override
    public String getName() {
        return "LexicalCoverage";
    }

    @Override
    public int getOrder() {
        return 12;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        String question = context == null ? "" : context.getMainQuestion();
        return question != null && ANCHORS.stream().anyMatch(question::contains);
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                       List<SearchChannelResult> results,
                                       SearchContext context) {
        if (chunks == null || chunks.isEmpty() || results == null) {
            return chunks;
        }
        int limit = context.getBudget().contextTopK();
        List<RetrievedChunk> output = new ArrayList<>(chunks);
        Set<String> selected = new LinkedHashSet<>();
        output.forEach(chunk -> selected.add(chunk.getId()));
        List<RetrievedChunk> raw = results.stream().flatMap(result -> result.getChunks().stream()).toList();
        List<RetrievedChunk> lexicalCandidates = raw.stream()
                .filter(candidate -> candidate.getId() != null && !selected.contains(candidate.getId()))
                .filter(candidate -> lexicalAnchorCount(context.getMainQuestion(), candidate.getText()) >= 2)
                .sorted(Comparator.comparingInt((RetrievedChunk candidate) -> lexicalAnchorCount(context.getMainQuestion(), candidate.getText())).reversed())
                .toList();
        for (RetrievedChunk candidate : lexicalCandidates) {
            if (output.size() < limit) {
                output.add(candidate);
            } else if (limit > 0) {
                output.set(output.size() - 1, candidate);
            }
            if (limit > 0 && output.size() >= limit) {
                break;
            }
        }
        return output;
    }

    static int lexicalAnchorCount(String question, String text) {
        if (question == null || text == null) return 0;
        return (int) ANCHORS.stream().filter(anchor -> question.contains(anchor) && text.contains(anchor)).count();
    }
}
