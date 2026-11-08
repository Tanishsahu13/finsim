package io.github.mhnuk2007.finsim.registry.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RegistryProperties.class)
class RegistryConfiguration {}
