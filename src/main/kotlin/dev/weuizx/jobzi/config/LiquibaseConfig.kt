package dev.weuizx.jobzi.config

import liquibase.integration.spring.SpringLiquibase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class LiquibaseConfig {

    @Bean
    fun liquibase(dataSource: DataSource): SpringLiquibase {
        return SpringLiquibase().apply {
            this.dataSource = dataSource
            this.changeLog = "classpath:db/changelog/db.changelog-master.xml"
            this.isDropFirst = false
            this.defaultSchema = "public"
        }
    }
}