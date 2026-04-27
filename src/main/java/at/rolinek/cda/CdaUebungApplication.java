package at.rolinek.cda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CdaUebungApplication {
    public static void main(String[] args) {
        SpringApplication.run(CdaUebungApplication.class, args);
    }
}
