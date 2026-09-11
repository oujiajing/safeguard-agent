package com.safeguard.agent.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;

/**
 * Elasticsearch 客户端配置
 */
@Slf4j
@Configuration
@ConditionalOnClass(ElasticsearchClient.class)
@ConditionalOnProperty(name = "rag.keyword.type", havingValue = "es")
public class EsClientConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(KeywordProperties keywordProperties) {
        String uris = keywordProperties.getEs().getUris();
        URI[] hosts = Arrays.stream(StringUtils.commaDelimitedListToStringArray(uris))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(URI::create)
                .toArray(URI[]::new);

        Rest5Client restClient = Rest5Client.builder(hosts).build();
        Rest5ClientTransport transport = new Rest5ClientTransport(restClient, new JacksonJsonpMapper());
        log.info("Elasticsearch 关键词检索客户端已初始化, uris={}", uris);
        return new ElasticsearchClient(transport);
    }
}
