package com.guessword;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@SpringBootApplication
public class GuesswordApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuesswordApplication.class, args);
    }

    @Bean(name = "mysqlDatasource")
    @ConfigurationProperties("mysql.datasource")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "postgresDatasource")
    @ConfigurationProperties("postgres.datasource")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create().build();
    }
}
