package com.documents.security;

import com.myproperty.platform.security.IdentityContext;
import com.myproperty.platform.security.MissingIdentityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityContextTest {

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    @Test
    void headerTenantWinsOverBody() {
        IdentityContext context = new IdentityContext("user-1", "co-1", "ten-1", Set.of("ADMIN"));
        assertThrows(AccessDeniedException.class, () -> context.rejectOverride("co-other", null));
        assertThrows(AccessDeniedException.class, () -> context.rejectOverride(null, "ten-other"));
        context.rejectOverride("co-1", "ten-1");
        context.assertSameTenant("ten-1");
        assertThrows(AccessDeniedException.class, () -> context.assertSameTenant("ten-2"));
    }

    @Test
    void requireWithoutContextFails() {
        MissingIdentityException ex = assertThrows(MissingIdentityException.class, IdentityContext::require);
        assertEquals("Missing or invalid identity context", ex.getMessage());
    }
}
