package at.rolinek.cda.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppPropertiesTest {

    @Test
    void retentionDaysIsClampedToAtLeastOne() {
        AppProperties.Usage usage = new AppProperties().getUsage();
        usage.setRetentionDays(0);
        assertEquals(1, usage.getRetentionDays());
        usage.setRetentionDays(-5);
        assertEquals(1, usage.getRetentionDays());
        usage.setRetentionDays(30);
        assertEquals(30, usage.getRetentionDays());
    }
}
