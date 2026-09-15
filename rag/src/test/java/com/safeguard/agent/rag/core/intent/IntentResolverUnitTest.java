package com.safeguard.agent.rag.core.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.safeguard.agent.rag.core.rewrite.RewriteResult;
import com.safeguard.agent.rag.dto.SubQuestionIntent;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class IntentResolverUnitTest {

    @Test
    void classifiesEachDecomposedSubQuestion() {
        IntentClassifier classifier = mock(IntentClassifier.class);
        IntentNode scaffold = IntentNode.builder().id("scaffold").name("脚手架").build();
        IntentNode edge = IntentNode.builder().id("edge").name("临边防护").build();
        when(classifier.classifyTargets("脚手架怎么整改？"))
                .thenReturn(List.of(NodeScore.builder().node(scaffold).score(.92).build()));
        when(classifier.classifyTargets("临边怎么整改？"))
                .thenReturn(List.of(NodeScore.builder().node(edge).score(.88).build()));

        Executor direct = Runnable::run;
        IntentResolver resolver = new IntentResolver(classifier, direct);
        List<SubQuestionIntent> result = resolver.resolve(new RewriteResult(
                "脚手架和临边怎么整改？", List.of("脚手架怎么整改？", "临边怎么整改？")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).subQuestion()).contains("脚手架");
        assertThat(result.get(0).nodeScores()).extracting(NodeScore::getNode).containsExactly(scaffold);
        assertThat(result.get(1).nodeScores()).extracting(NodeScore::getNode).containsExactly(edge);
    }

    @Test
    void doesNotRouteReadOnlySafetyQuestionToWriteIntent() {
        IntentClassifier classifier = mock(IntentClassifier.class);
        IntentNode create = IntentNode.builder().id("create").name("创建整改工单")
                .kind(com.safeguard.agent.rag.enums.IntentKind.MCP)
                .mcpToolId("create_rectification_order").build();
        IntentNode issue = IntentNode.builder().id("issue").name("下发整改")
                .kind(com.safeguard.agent.rag.enums.IntentKind.MCP)
                .mcpToolId("issue_rectification").build();
        when(classifier.classifyTargets("安全帽佩戴要求"))
                .thenReturn(List.of(NodeScore.builder().node(issue).score(.95).build()));
        when(classifier.classifyTargets("请创建整改工单"))
                .thenReturn(List.of(NodeScore.builder().node(create).score(.95).build()));

        IntentResolver resolver = new IntentResolver(classifier, Runnable::run);

        assertThat(resolver.resolve(new RewriteResult("安全帽佩戴要求", List.of("安全帽佩戴要求")))
                .get(0).nodeScores()).isEmpty();
        assertThat(resolver.resolve(new RewriteResult("请创建整改工单", List.of("请创建整改工单")))
                .get(0).nodeScores()).hasSize(1);
    }
}
