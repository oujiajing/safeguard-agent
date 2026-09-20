package com.safeguard.agent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.agent.attachment.AgentImageAttachmentService;
import com.safeguard.agent.agent.integration.safeteam.SafeTeamApiClient;
import com.safeguard.agent.agent.integration.safeteam.SafeTeamIntegrationProperties;
import com.safeguard.agent.rag.eval.HazardAssessmentService;
import com.safeguard.agent.rag.eval.VisualHazardService;
import io.agentscope.core.tool.AgentTool;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafetyAgentToolFactoryTest {

    @Test
    void exposesOnlyReadOnlyCapabilitiesWhenSafeTeamWritesAreDisabled() {
        SafetyAgentToolFactory factory = factory(false);

        assertThat(factory.createTools()).extracting(AgentTool::getName).containsExactly(
                SafetyAgentToolFactory.ASSESS_TOOL, SafetyAgentToolFactory.VISUAL_TOOL);
    }

    @Test
    void exposesWriteCapabilityOnlyWhenSafeTeamWritesAreEnabled() {
        SafetyAgentToolFactory factory = factory(true);

        List<AgentTool> tools = factory.createTools();

        assertThat(tools).extracting(AgentTool::getName).containsExactly(
                SafetyAgentToolFactory.ASSESS_TOOL,
                SafetyAgentToolFactory.VISUAL_TOOL,
                SafetyAgentToolFactory.CREATE_TOOL);
        assertThat(tools).extracting(AgentTool::isReadOnly).containsExactly(true, true, false);
    }

    private SafetyAgentToolFactory factory(boolean safeTeamEnabled) {
        SafeTeamIntegrationProperties properties = new SafeTeamIntegrationProperties();
        properties.setEnabled(safeTeamEnabled);
        SafetyAgentToolFactory factory = new SafetyAgentToolFactory(
                mock(HazardAssessmentService.class), mock(VisualHazardService.class),
                mock(AgentImageAttachmentService.class), new ObjectMapper(), mock(SafeTeamApiClient.class), properties);
        return factory;
    }
}
