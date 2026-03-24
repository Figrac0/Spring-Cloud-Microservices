package com.unik.company_service.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
public class JpaFlywayConfiguration extends AbstractDependsOnBeanFactoryPostProcessor {
    public JpaFlywayConfiguration() {
        super(EntityManagerFactory.class, "flyway");
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(
            DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:db/migration}") String[] locations,
            @Value("${spring.flyway.schemas:}") String[] schemas,
            @Value("${spring.flyway.default-schema:}") String defaultSchema,
            @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate,
            @Value("${spring.flyway.clean-disabled:true}") boolean cleanDisabled,
            @Value("${spring.flyway.create-schemas:false}") boolean createSchemas) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(filterNonBlank(locations))
                .schemas(filterNonBlank(schemas))
                .baselineOnMigrate(baselineOnMigrate)
                .cleanDisabled(cleanDisabled)
                .createSchemas(createSchemas);

        if (StringUtils.hasText(defaultSchema)) {
            configuration.defaultSchema(defaultSchema);
        }

        return configuration.load();
    }

    private static String[] filterNonBlank(String[] values) {
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }
}
