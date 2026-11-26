package org.example.school_wallet.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Cors cors = new Cors();
    private Bootstrap bootstrap = new Bootstrap();
    private Auth auth = new Auth();

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "";
        private String issuer = "school-wallet";
        private long accessTokenMinutes = 15;
        private long refreshTokenDays = 30;

        public Duration accessTokenTtl() {
            return Duration.ofMinutes(accessTokenMinutes);
        }

        public Duration refreshTokenTtl() {
            return Duration.ofDays(refreshTokenDays);
        }
    }

    @Getter
    @Setter
    public static class Security {
        private String dataEncryptionKey = "";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    @Getter
    @Setter
    public static class Bootstrap {
        private boolean enabled = true;
        private String superAdminUsername = "superadmin";
        private String superAdminPhone = "+255700000000";
        private String superAdminPassword = "";
        private String superAdminFullName = "KADE Super Admin";
    }

    @Getter
    @Setter
    public static class Auth {
        private boolean passwordResetExposeToken = false;
        private long passwordResetMinutes = 30;
    }
}
