package com.saltlux.nac.config;

import com.saltlux.nac.elecdoc.DocumentExtractProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentExtractProperties.class)
public class DocumentExtractConfig {
}
