package com.documents;

import com.myproperty.platform.security.GatewaySecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = GatewaySecurityAutoConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "gateway.identity.hmac-secret=dev-gateway-internal-secret")
class DocumentsApplicationContextTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void applicationIsIndependentlyRunnable() {
        assertNotNull(DocumentsApplication.class.getAnnotation(SpringBootApplication.class));
        assertNotNull(securityFilterChain);
    }
}
