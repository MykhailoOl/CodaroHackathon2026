package com.example.hackathoncodaro2026.validation;

import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DeceasedDatesValidator implements ConstraintValidator<DeceasedDatesValid, ArrangementRequest> {

    @Override
    public boolean isValid(ArrangementRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getDateOfDeath() == null || request.getDateOfBirth() == null) {
            return true;
        }
        return !request.getDateOfDeath().isBefore(request.getDateOfBirth());
    }
}
