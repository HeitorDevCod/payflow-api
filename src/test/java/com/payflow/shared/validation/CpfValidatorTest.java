package com.payflow.shared.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "529.982.247-25", "111.444.777-35", "39053344705"})
    void acceptsValidCpf(String cpf) {
        assertThat(CpfValidator.isValid(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224724", "00000000000", "11111111111", "1234567890", "abc", ""})
    void rejectsInvalidCpf(String cpf) {
        assertThat(CpfValidator.isValid(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25"})
    void normalizesFormatting(String cpf) {
        assertThat(CpfValidator.normalize(cpf)).isEqualTo("52998224725");
    }
}
