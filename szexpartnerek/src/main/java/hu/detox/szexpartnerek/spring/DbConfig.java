package hu.detox.szexpartnerek.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;


@Configuration
class DbConfig {
    @Bean
    @ConfigurationProperties(prefix = "szexpartnerek.datasource", ignoreInvalidFields = true)
    HikariConfig szexpartnerekHC() {
        return new HikariConfig();
    }

    @Bean
    DataSource szexpartnerekDS(@Qualifier("szexpartnerekHC") HikariConfig config) {
        return new HikariDataSource(config);
    }

    @Bean
    JdbcTemplate szexpartnerekJDBC(@Qualifier("szexpartnerekDS") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    @ConfigurationProperties(prefix = "szexpartnerek.flyway")
    ClassicConfiguration szexpartnerekFWC() {
        return new ClassicConfiguration();
    }

    @Bean
    Flyway szexpartnerekFW(
            @Qualifier("szexpartnerekDS") DataSource szexpartnerekDS,
            @Qualifier("szexpartnerekFWC") ClassicConfiguration cfg) {
        cfg.setDataSource(szexpartnerekDS);
        var fw = new Flyway(cfg);
        fw.migrate();
        return fw;
    }
}
