package com.safeguard.agent.rag.core.vector.strategy;

import com.safeguard.agent.rag.core.retrieval.RetrieveRequest;
import com.safeguard.agent.rag.core.vector.VectorRetrieverService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionParallelRetrieverTest {

    @Test
    @DisplayName("fan-out全库检索时同一个子问题只生成一次Query向量")
    void fanOutGlobalRetrievalEmbedSubQuestionOnce() {
        VectorRetrieverService retrieverService = mock(VectorRetrieverService.class);
        when(retrieverService.embedAndNormalize("报销流程")).thenReturn(new float[]{0.6F, 0.8F});
        when(retrieverService.retrieveByVector(any(float[].class), any(RetrieveRequest.class))).thenReturn(List.of());

        CollectionParallelRetriever retriever = new CollectionParallelRetriever(retrieverService, Runnable::run);
        retriever.executeParallelRetrieval("报销流程", List.of("kb-finance", "kb-policy"), 7);

        verify(retrieverService, times(1)).embedAndNormalize("报销流程");
        verify(retrieverService, times(2)).retrieveByVector(any(float[].class), any(RetrieveRequest.class));
    }
}
