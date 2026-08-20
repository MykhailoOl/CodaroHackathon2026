package com.example.hackathoncodaro2026.controller;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;

@ControllerAdvice
public class ArrangementBindingAdvice {

    @InitBinder
    public void bindAttendees(WebDataBinder binder) {
        binder.registerCustomEditor(Integer.class, "attendees", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null) {
                    setValue(null);
                    return;
                }
                String digits = text.replaceAll("[^0-9]", "");
                setValue(digits.isEmpty() ? null : Integer.valueOf(digits));
            }
        });
    }
}
