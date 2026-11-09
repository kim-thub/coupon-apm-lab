package lab.couponapm.coupon;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class PostgresSupportIT {
    @Autowired private JdbcTemplate cleanupJdbc;
    private final List<Long> ownedCouponIds = new CopyOnWriteArrayList<>();

    protected long ownCoupon(long id) {
        ownedCouponIds.add(id);
        return id;
    }

    @AfterEach
    void cleanOnlyOwnedFixtureAfterTasksEnd() {
        for (Long couponId : ownedCouponIds) {
            cleanupJdbc.update("DELETE FROM coupon_issues WHERE coupon_id=?", couponId);
            cleanupJdbc.update("DELETE FROM coupons WHERE id=?", couponId);
        }
        ownedCouponIds.clear();
    }

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.6")
            .withDatabaseName("coupon_test")
            .withUsername("test")
            .withPassword("test");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void setupDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "32");
    }
}
