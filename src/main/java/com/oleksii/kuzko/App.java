package com.oleksii.kuzko;

//import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * @author The Weather Company, An IBM Business
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean(name = "mysqlDatasource")
    @ConfigurationProperties("mysql.datasource")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    /*@Bean
    public SpringLiquibase liquibase() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setChangeLog("classpath:db/changelog.xml");
        liquibase.setDataSource(postgresDataSource());
        return liquibase;
    }*/

    @Primary
    @Bean(name = "postgresDatasource")
    @ConfigurationProperties("postgres.datasource")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create().build();
    }
}
