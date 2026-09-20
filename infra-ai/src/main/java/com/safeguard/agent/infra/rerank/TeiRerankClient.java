package com.safeguard.agent.infra.rerank;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.infra.enums.ModelCapability;
import com.safeguard.agent.infra.enums.ModelProvider;
import com.safeguard.agent.infra.http.HttpMediaTypes;
import com.safeguard.agent.infra.http.HttpResponseHelper;
import com.safeguard.agent.infra.http.ModelClientErrorType;
import com.safeguard.agent.infra.http.ModelClientException;
import com.safeguard.agent.infra.http.ModelUrlResolver;
import com.safeguard.agent.infra.model.ModelTarget;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** TEI text reranker client for bge-reranker-v2-m3. */
@Service
@RequiredArgsConstructor
public class TeiRerankClient implements RerankClient {

    @Qualifier("syncHttpClient")
    private final OkHttpClient httpClient;

    @Override
    public String provider() {
        return ModelProvider.TEI.getId();
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target) {
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }
        List<RetrievedChunk> dedup = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RetrievedChunk candidate : candidates) {
            if (seen.add(candidate.getId())) {
                dedup.add(candidate);
            }
        }

        JsonObject body = new JsonObject();
        body.addProperty("query", query == null ? "" : query);
        JsonArray texts = new JsonArray();
        for (RetrievedChunk candidate : dedup) {
            texts.add(candidate.getText() == null ? "" : candidate.getText());
        }
        body.add("texts", texts);

        Request request = new Request.Builder()
                .url(ModelUrlResolver.resolveUrl(target.provider(), target.candidate(), ModelCapability.RERANK))
                .post(RequestBody.create(body.toString(), HttpMediaTypes.JSON))
                .build();

        JsonArray response;
        try (Response result = httpClient.newCall(request).execute()) {
            if (!result.isSuccessful()) {
                throw new ModelClientException(provider() + " rerank 请求失败: HTTP " + result.code(),
                        ModelClientErrorType.fromHttpStatus(result.code()), result.code());
            }
            JsonElement parsed = JsonParser.parseString(HttpResponseHelper.readBody(result.body()));
            if (!parsed.isJsonArray()) {
                throw new ModelClientException(provider() + " rerank 响应不是数组",
                        ModelClientErrorType.INVALID_RESPONSE, null);
            }
            response = parsed.getAsJsonArray();
        } catch (IOException e) {
            throw new ModelClientException(provider() + " rerank 请求失败: " + e.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR, null, e);
        }

        List<RetrievedChunk> reranked = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (JsonElement element : response) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!item.has("index") || !item.has("score")) continue;
            int index = item.get("index").getAsInt();
            if (index < 0 || index >= dedup.size()) continue;
            RetrievedChunk source = dedup.get(index);
            if (added.add(source.getId())) {
                float score = item.get("score").getAsFloat();
                reranked.add(source.toBuilder().score(score).rerankScore(score).build());
            }
            if (reranked.size() >= Math.min(topN, dedup.size())) break;
        }
        return reranked;
    }
}
