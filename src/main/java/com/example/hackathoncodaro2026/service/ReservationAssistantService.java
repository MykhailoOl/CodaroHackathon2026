package com.example.hackathoncodaro2026.service;

import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.assistant.AssistantCreateResponse;
import com.example.hackathoncodaro2026.dto.assistant.AssistantExtraOptionDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantHomeDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantPreviewDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantQuoteDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantSessionDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantVenueCardDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantVenueDetailDto;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.ServiceType;

import java.util.List;

public interface ReservationAssistantService {

    AssistantSessionDto session(User user);

    List<AssistantHomeDto> homes();

    List<AssistantVenueCardDto> venues(Long homeId);

    AssistantVenueDetailDto venue(Long venueId);

    List<AssistantExtraOptionDto> extras(Long venueId, ServiceType serviceType);

    AssistantQuoteDto quote(User user, ArrangementRequest request);

    AssistantPreviewDto preview(User user, ArrangementRequest request);

    AssistantCreateResponse spin(User user, ArrangementRequest request);
}
