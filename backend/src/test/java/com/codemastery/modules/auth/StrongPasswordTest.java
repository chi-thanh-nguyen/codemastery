package com.codemastery.modules.auth;

import com.codemastery.common.validation.StrongPassword;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordTest {

    private static final String SUPPLEMENTARY = "\uD83D\uDE00";
    private static final String DEFAULT_MESSAGE =
            "Password must contain at least 15 Unicode code points and at most 72 UTF-8 bytes";

    // Literal counts are independent oracles: ASCII = 1 byte, é = 2, 漢 = 3,
    // U+1F600 = 4; e + U+0301 is two code points and three UTF-8 bytes.
    static Stream<Arguments> passwordBoundaries() {
        return Stream.of(
                Arguments.of("empty", "", 0, 0, false),
                Arguments.of("14 ASCII code points", "a".repeat(14), 14, 14, false),
                Arguments.of("15 ASCII code points / lowercase only", "a".repeat(15), 15, 15, true),
                Arguments.of("72 ASCII bytes", "a".repeat(72), 72, 72, true),
                Arguments.of("73 ASCII bytes", "a".repeat(73), 73, 73, false),
                Arguments.of("14 supplementary code points, 28 UTF-16 units",
                        SUPPLEMENTARY.repeat(14), 14, 56, false),
                Arguments.of("15 supplementary code points", SUPPLEMENTARY.repeat(15), 15, 60, true),
                Arguments.of("18 supplementary code points / 72 bytes", SUPPLEMENTARY.repeat(18), 18, 72, true),
                Arguments.of("19 supplementary code points / 76 bytes", SUPPLEMENTARY.repeat(19), 19, 76, false),
                Arguments.of("mixed 14 code points", "a".repeat(13) + SUPPLEMENTARY, 14, 17, false),
                Arguments.of("mixed 15 code points", "a".repeat(14) + SUPPLEMENTARY, 15, 18, true),
                Arguments.of("15 two-byte code points", "\u00E9".repeat(15), 15, 30, true),
                Arguments.of("15 three-byte code points", "\u6F22".repeat(15), 15, 45, true),
                Arguments.of("mixed 72 UTF-8 bytes", SUPPLEMENTARY.repeat(17) + "abcd", 21, 72, true),
                Arguments.of("mixed 73 UTF-8 bytes", SUPPLEMENTARY.repeat(17) + "abcde", 22, 73, false),
                Arguments.of("24 three-byte code points / 72 bytes", "\u6F22".repeat(24), 24, 72, true),
                Arguments.of("25 three-byte code points / 75 bytes", "\u6F22".repeat(25), 25, 75, false),
                Arguments.of("14 spaces", " ".repeat(14), 14, 14, false),
                Arguments.of("15 spaces", " ".repeat(15), 15, 15, true),
                Arguments.of("72 spaces", " ".repeat(72), 72, 72, true),
                Arguments.of("73 spaces", " ".repeat(73), 73, 73, false),
                Arguments.of("leading and trailing spaces count", " " + "a".repeat(13) + " ", 15, 15, true),
                Arguments.of("leading tab and trailing newline count", "\t" + "a".repeat(13) + "\n", 15, 15, true),
                Arguments.of("uppercase only", "A".repeat(15), 15, 15, true),
                Arguments.of("numeric only", "1".repeat(15), 15, 15, true),
                Arguments.of("symbol only", "!".repeat(15), 15, 15, true),
                Arguments.of("14 precomposed accents", "\u00E9".repeat(14), 14, 28, false),
                Arguments.of("14 decomposed accents are 28 code points", "e\u0301".repeat(14), 28, 42, true),
                Arguments.of("36 precomposed accents fit 72 bytes", "\u00E9".repeat(36), 36, 72, true),
                Arguments.of("36 decomposed accents exceed 72 bytes", "e\u0301".repeat(36), 72, 108, false));
    }

    static Stream<Arguments> charSequences() {
        String value = SUPPLEMENTARY.repeat(18);
        return Stream.of(
                Arguments.of("String", value),
                Arguments.of("StringBuilder", new StringBuilder(value)),
                Arguments.of("StringBuffer", new StringBuffer(value)),
                Arguments.of("CharBuffer", CharBuffer.wrap(value)));
    }

    @Nested
    class PredicateTests {

        private final StrongPassword.Validator predicate = new StrongPassword.Validator();

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.codemastery.modules.auth.StrongPasswordTest#passwordBoundaries")
        void enforcesExplicitBoundsWithoutTrimmingNormalizationOrCharacterClasses(
                String scenario, String value, int codePoints, int utf8Bytes, boolean expected) {
            assertEquals(codePoints, value.codePointCount(0, value.length()), "fixture code points");
            assertEquals(utf8Bytes, value.getBytes(StandardCharsets.UTF_8).length, "fixture UTF-8 bytes");
            assertEquals(expected, predicate.isValid(value, null), scenario);
        }

        @Test
        void leavesNullRequirednessToASeparateConstraint() {
            assertTrue(predicate.isValid(null, null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.codemastery.modules.auth.StrongPasswordTest#charSequences")
        void acceptsSupportedCharSequencesAtTheUnicodeByteBoundary(String type, CharSequence value) {
            assertTrue(predicate.isValid(value, null), type);
        }
    }

    @Nested
    class BeanValidationTests {

        private static ValidatorFactory factory;
        private static Validator validator;

        @BeforeAll
        static void openProvider() {
            factory = Validation.buildDefaultValidatorFactory();
            validator = factory.getValidator();
        }

        @AfterAll
        static void closeProvider() {
            if (factory != null) {
                factory.close();
            }
        }

        @Test
        void standaloneNullPassesButRequiredNullFailsOnlyNotNull() {
            assertTrue(validator.validate(new ShapeOnly(null)).isEmpty());
            var violations = validator.validate(new RequiredPassword(null));
            assertEquals(1, violations.size());
            var violation = violations.iterator().next();
            assertEquals(NotNull.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
            assertEquals("password", violation.getPropertyPath().toString());
        }

        static Stream<Arguments> providerCases() {
            return Stream.of(
                    Arguments.of("short", "a".repeat(14), false),
                    Arguments.of("minimum", "a".repeat(15), true),
                    Arguments.of("Unicode maximum", SUPPLEMENTARY.repeat(18), true),
                    Arguments.of("Unicode overflow", SUPPLEMENTARY.repeat(19), false),
                    Arguments.of("spaces are not absent", " ".repeat(15), true));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("providerCases")
        void providerExecutesTheConstraintAloneAndWithRequiredness(
                String scenario, String value, boolean expected) {
            int expectedViolations = expected ? 0 : 1;
            assertEquals(expectedViolations, validator.validate(new ShapeOnly(value)).size(), scenario);
            var violations = validator.validate(new RequiredPassword(value));
            assertEquals(expectedViolations, violations.size(), scenario);
            if (!expected) {
                assertEquals(StrongPassword.class,
                        violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType());
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.codemastery.modules.auth.StrongPasswordTest#charSequences")
        void providerBindsTheCharSequenceValidator(String type, CharSequence value) {
            assertTrue(validator.validate(new ShapeOnly(value)).isEmpty(), type);
        }

        @Test
        void defaultViolationDescribesThePolicyWithoutEchoingThePassword() {
            String syntheticInput = "demo-only";
            var violations = validator.validate(new ShapeOnly(syntheticInput));
            assertEquals(1, violations.size());
            var violation = violations.iterator().next();
            assertEquals(StrongPassword.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
            assertEquals(DEFAULT_MESSAGE, violation.getMessage());
            assertEquals("password", violation.getPropertyPath().toString());
            assertFalse(violation.getMessage().contains(syntheticInput));
        }
    }

    private static final class ShapeOnly {

        @StrongPassword
        private final CharSequence password;

        private ShapeOnly(CharSequence password) {
            this.password = password;
        }
    }

    private static final class RequiredPassword {

        @NotNull
        @StrongPassword
        private final CharSequence password;

        private RequiredPassword(CharSequence password) {
            this.password = password;
        }
    }
}
