package at.rolinek.cda.usage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IpPrefixTest {

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @CsvSource(value = {
        "203.0.113.7|203.0.113.0",
        "10.0.0.255|10.0.0.0",
        "2001:db8:1:2:3:4:5:6|2001:db8:1::",
        "2001:0db8:0001:ffff::1|2001:db8:1::",
        "::ffff:203.0.113.7|203.0.113.0",
        "[2001:db8:1::5]|2001:db8:1::",
        "fe80::1%eth0|fe80:0:0::",
        "::1|0:0:0::",
        "unknown|",
        "|",
        "999.1.1.1|",
        "not an ip|",
    }, delimiter = '|', nullValues = "NULL")
    void shortensOrBlanks(String ip, String expected) {
        assertThat(IpPrefix.of(ip)).isEqualTo(expected == null ? "" : expected);
    }
}
