package hu.detox.szexpartnerek.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import hu.detox.config.Cfg2PropertySourceFactory;
import hu.detox.spring.DetoxConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import javax.sql.DataSource;


@Configuration
@ComponentScan(basePackageClasses = DetoxConfig.class)
@PropertySource(value = "classpath:szexpartnerek.yaml", factory = Cfg2PropertySourceFactory.class)
public class SzexConfig {
    private static JdbcTemplate jdbc;

    public static <T extends @Nullable Object> T query(String sql, ResultSetExtractor<T> rse) throws DataAccessException {
        return jdbc.query(sql, rse);
    }

    public static void exec(String sql) throws DataAccessException {
        jdbc.execute(sql);
    }

    public static JdbcTemplate jdbc() {
        return jdbc;
    }

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
        jdbc = new JdbcTemplate(ds);
        return jdbc;
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
