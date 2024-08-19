/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.PgVectorStore.PgIndexType;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

/**
 * Integration tests for observation instruAbstractObservationVectorStorementation in
 * {@link OpenAiChatModel}.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@Testcontainers
public class PgVectorObservationIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
		.withUsername("postgres")
		.withPassword("postgres");

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class)
		.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",

				// JdbcTemplate configuration
				String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"app.datasource.username=postgres", "app.datasource.password=postgres",
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	@Test
	void observationVectorStoreAddAndQueryOperations() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(documents);

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("vector_store pg_vector add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(), "pg_vector")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(), "vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "public")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.FIELD_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), "cosine")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.TOP_K.asString(), "none")

				.hasBeenStarted()
				.hasBeenStopped();

			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("What is Great Depression").withTopK(1));

			assertThat(results).isNotEmpty();

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("vector_store pg_vector query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(), "pg_vector")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")

				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(), "vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "public")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.FIELD_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), "cosine")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.TOP_K.asString(), "1")

				.hasBeenStarted()
				.hasBeenStopped();

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
				ObservationRegistry observationRegistry) {
			return new PgVectorStore.Builder(jdbcTemplate, embeddingModel)
				.withDistanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
				.withIndexType(PgIndexType.HNSW)
				.withObservationRegistry(observationRegistry)
				.withInitializeSchema(true)
				.build();
		}

		@Bean
		public JdbcTemplate myJdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		@Primary
		@ConfigurationProperties("app.datasource")
		public DataSourceProperties dataSourceProperties() {
			return new DataSourceProperties();
		}

		@Bean
		public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}