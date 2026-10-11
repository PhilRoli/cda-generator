package at.rolinek.cda.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiFillStateSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonKeysMatchTheFrontendStateShape() {
        JsonNode json = mapper.valueToTree(AiFillStateFixtures.sample());

        assertThat(json.path("patient").path("givenName").asText()).isEqualTo("Franz");
        assertThat(json.path("patient").path("address").path("postalCode").asText()).isEqualTo("4020");
        assertThat(json.path("encounter").path("dischargeDate").asText()).isEqualTo("2026-10-11T11:00");
        assertThat(json.path("diagnosen").get(0).path("text").asText()).startsWith("Chronische");
        assertThat(json.path("medikation").get(0).path("schema").asText()).isEqualTo("1-0-0");
        assertThat(json.path("allergien").get(0).path("substanz").asText()).isEqualTo("Penicillin");
        assertThat(json.path("risikofaktoren").get(0).path("faktor").asText()).isEqualTo("Nikotinabusus");
        assertThat(json.path("patientenverfuegung").path("status").asText()).isEqualTo("keine");
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        String json = mapper.writeValueAsString(AiFillStateFixtures.sample());
        assertThat(mapper.readValue(json, AiFillState.class)).isEqualTo(AiFillStateFixtures.sample());
    }
}
