package ru.nstu.system.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reactive API gateway (Spring Cloud Gateway / WebFlux).
 *
 * <p>The component scan is deliberately narrow: only the gateway's own package and
 * {@code ru.nstu.system.security} are scanned. Scanning the whole
 * {@code ru.nstu.system} tree would activate {@code NstuRabbitConfig} from the
 * {@code ru.nstu:contracts} module, which this reactive service must never load
 * (design.md D4, D21). The JWT parser/issuer and the shared password-change policy
 * live in {@code ru.nstu.system.security} and are picked up from there.</p>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.nstu.system.gateway",
        "ru.nstu.system.security"
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
