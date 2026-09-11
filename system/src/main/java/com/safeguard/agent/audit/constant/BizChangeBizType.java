package com.safeguard.agent.audit.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BizChangeBizType {

    public static final String KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
    public static final String KNOWLEDGE_DOCUMENT = "KNOWLEDGE_DOCUMENT";
    public static final String KNOWLEDGE_CHUNK = "KNOWLEDGE_CHUNK";
    public static final String INGESTION_PIPELINE = "INGESTION_PIPELINE";
    public static final String INGESTION_TASK = "INGESTION_TASK";
    public static final String INTENT_TREE = "INTENT_TREE";
    public static final String QUERY_TERM_MAPPING = "QUERY_TERM_MAPPING";
    public static final String SAMPLE_QUESTION = "SAMPLE_QUESTION";
    public static final String USER = "USER";
    public static final String AGENT_PROFILE = "AGENT_PROFILE";
    public static final String AGENT_SKILL = "AGENT_SKILL";
}
