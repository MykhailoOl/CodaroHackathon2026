package com.example.hackathoncodaro2026.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DeceasedDatesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeceasedDatesValid {

    String message() default "Date of death cannot be earlier than date of birth";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
