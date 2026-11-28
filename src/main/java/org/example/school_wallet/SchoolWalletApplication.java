package org.example.school_wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"org.example.school_wallet", "com.skulify.pocketmoney", "com.skulify.retail"})
@EntityScan(basePackages = {"org.example.school_wallet", "com.skulify.pocketmoney", "com.skulify.retail"})
@EnableJpaRepositories(basePackages = {"org.example.school_wallet", "com.skulify.pocketmoney", "com.skulify.retail"})
public class SchoolWalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolWalletApplication.class, args);
    }

}
