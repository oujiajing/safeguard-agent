package com.safeguard.agent.legal.persistence;

public record LegalPersistenceResult(String documentId, String status, int elementCount, int clauseCount, int chunkCount) {
}
