package com.safeguard.agent.ingestion.strategy.fetcher;

import com.safeguard.agent.framework.exception.ServiceException;
import com.safeguard.agent.ingestion.domain.context.DocumentSource;
import com.safeguard.agent.ingestion.domain.enums.SourceType;
import com.safeguard.agent.ingestion.util.HttpClientHelper;
import com.safeguard.agent.core.parser.mime.MimeTypeDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 链接文档获取器
 * 用于从指定的 HTTP/HTTPS 链接地址获取文档内容
 */
@Component
@RequiredArgsConstructor
public class HttpUrlFetcher implements DocumentFetcher {

    private final HttpClientHelper httpClientHelper;

    @Override
    public SourceType supportedType() {
        return SourceType.URL;
    }

    @Override
    public FetchResult fetch(DocumentSource source) {
        String location = source.getLocation();
        if (!StringUtils.hasText(location)) {
            throw new ServiceException("链接地址不能为空");
        }

        Map<String, String> headers = buildHeaders(source.getCredentials());
        HttpClientHelper.HttpFetchResponse resp = httpClientHelper.get(location, headers);
        String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : resp.fileName();
        String contentType = normalizeContentType(resp.contentType());
        if (!StringUtils.hasText(contentType)) {
            contentType = MimeTypeDetector.detect(resp.body(), fileName);
        }
        return new FetchResult(resp.body(), contentType, fileName);
    }

    private Map<String, String> buildHeaders(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return Map.of();
        }
        Map<String, String> headers = new HashMap<>();
        credentials.forEach((k, v) -> {
            if (!StringUtils.hasText(k) || v == null) {
                return;
            }
            if ("token".equalsIgnoreCase(k)) {
                headers.put("Authorization", "Bearer " + v);
            } else {
                headers.put(k, v);
            }
        });
        return headers;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        int idx = contentType.indexOf(';');
        return idx > 0 ? contentType.substring(0, idx).trim() : contentType.trim();
    }
}
