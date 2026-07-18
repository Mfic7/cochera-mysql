package pe.cochera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CocheraApp {

    public static void main(String[] args) {
        SpringApplication.run(CocheraApp.class, args);
    }
}
