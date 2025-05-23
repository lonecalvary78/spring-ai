/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.model.minimax.autoconfigure;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.minimax.MiniMaxEmbeddingOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for MiniMax embedding model.
 *
 * @author Geng Rong
 */
@ConfigurationProperties(MiniMaxEmbeddingProperties.CONFIG_PREFIX)
public class MiniMaxEmbeddingProperties extends MiniMaxParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.minimax.embedding";

	public static final String DEFAULT_EMBEDDING_MODEL = MiniMaxApi.EmbeddingModel.Embo_01.value;

	private MetadataMode metadataMode = MetadataMode.EMBED;

	@NestedConfigurationProperty
	private MiniMaxEmbeddingOptions options = MiniMaxEmbeddingOptions.builder().model(DEFAULT_EMBEDDING_MODEL).build();

	public MiniMaxEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(MiniMaxEmbeddingOptions options) {
		this.options = options;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

}
