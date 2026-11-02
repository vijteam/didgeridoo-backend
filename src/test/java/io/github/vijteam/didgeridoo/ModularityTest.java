package io.github.vijteam.didgeridoo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void shouldVerifyModuleDependencies() {
        ApplicationModules.of(DidgeridooApplication.class).verify();
    }
}
