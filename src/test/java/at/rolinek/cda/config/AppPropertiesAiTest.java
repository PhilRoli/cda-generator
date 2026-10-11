package at.rolinek.cda.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesAiTest {

    private final AppProperties.Ai ai = new AppProperties().getAi();

    @Test
    void defaultsAreDisabledHaikuWithConservativeLimits() {
        assertThat(ai.enabled()).isFalse();
        assertThat(ai.getModel()).isEqualTo("claude-haiku-5-5");
        assertThat(ai.getUserDailyLimit()).isEqualTo(20);
        assertThat(ai.getMonthlyBudgetUsd()).isEqualTo(40.0);
        assertThat(ai.getMaxPromptChars()).isEqualTo(1000);
        assertThat(ai.getTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void blankOrNullKeyKeepsFeatureDisabled() {
        ai.setApiKey("   ");
        assertThat(ai.enabled()).isFalse();
        ai.setApiKey(null);
        assertThat(ai.enabled()).isFalse();
        ai.setApiKey(" sk-test ");
        assertThat(ai.enabled()).isTrue();
        assertThat(ai.getApiKey()).isEqualTo("sk-test");
    }

    @Test
    void costUsesPricesPerMillionTokens() {
        assertThat(ai.costUsd(1_000_000, 1_000_000)).isEqualTo(0.60, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(ai.costUsd(2_000, 500)).isEqualTo(0.00045, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(ai.costUsd(0, 0)).isZero();
    }

    @Test
    void limitsAreClampedToSaneMinimums() {
        ai.setUserDailyLimit(-5);
        ai.setMaxPromptChars(0);
        ai.setTimeoutSeconds(0);
        assertThat(ai.getUserDailyLimit()).isEqualTo(1);
        assertThat(ai.getMaxPromptChars()).isEqualTo(1);
        assertThat(ai.getTimeoutSeconds()).isEqualTo(1);
    }
}
