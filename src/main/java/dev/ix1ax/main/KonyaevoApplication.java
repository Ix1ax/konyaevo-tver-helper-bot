package dev.ix1ax.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KonyaevoApplication {

    public static void main(String[] args) {
        SpringApplication.run(KonyaevoApplication.class, args);
    }
}
