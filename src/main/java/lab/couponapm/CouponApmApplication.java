package lab.couponapm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CouponApmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponApmApplication.class, args);
    }
}
