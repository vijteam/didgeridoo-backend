package io.github.vijteam.didgeridoo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(
            name = "test.postgres.container",
            havingValue = "true",
            matchIfMissing = true)
    PostgreSQLContainer postgresContainer(
            @org.springframework.beans.factory.annotation.Value(
                            "${test.postgres.image:postgres:17}")
                    String image) {
        return new PostgreSQLContainer(image);
    }
}
