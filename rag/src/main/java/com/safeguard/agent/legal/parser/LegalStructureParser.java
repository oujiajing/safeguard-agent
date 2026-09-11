package com.safeguard.agent.legal.parser;

import com.safeguard.agent.legal.model.LegalDocumentElement;
import com.safeguard.agent.legal.model.LegalDocumentMetadata;
import com.safeguard.agent.legal.model.NormalizedLegalDocument;

import java.util.List;

public interface LegalStructureParser {

    NormalizedLegalDocument parse(LegalDocumentMetadata metadata,
                                  List<LegalDocumentElement> elements,
                                  List<String> initialWarnings);
}
