package com.safeguard.agent.legal.diagnostics;

public record LegalUnstructuredItem(
        String document,
        int elementOrder,
        String rawText,
        UnstructuredDiagnosticType diagnosticType
) {
}
