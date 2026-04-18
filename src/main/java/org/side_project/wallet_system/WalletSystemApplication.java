package org.side_project.wallet_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WalletSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletSystemApplication.class, args);
    }

}
