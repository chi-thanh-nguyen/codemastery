package com.codemastery.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

@Documented
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must contain at least 15 Unicode code points and at most 72 UTF-8 bytes";
    
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StrongPassword, CharSequence> {

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            final int codePoints = Character.codePointCount(value, 0, value.length());
            if (codePoints < 15) {
                return false;
            }
            final int utf8Bytes = value.toString().getBytes(StandardCharsets.UTF_8).length;
            return utf8Bytes <= 72;
        }
    }
}