package com.example.hackathoncodaro2026.intent.parse;

import com.example.hackathoncodaro2026.config.DomainProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.TimeOfDay;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CodaroIntentParser implements IntentParser {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String TOOL_NAME = "emit_intent_spec";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final IntentProperties config;
    private final DomainProperties domain;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CodaroIntentParser(
            @Value("${intent.llm.base-url:}") String baseUrl,
            @Value("${intent.llm.api-key:}") String apiKey,
            @Value("${intent.llm.model:}") String model,
            IntentProperties config,
            DomainProperties domain
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.config = config;
        this.domain = domain;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty() && !model.isEmpty();
    }

    @Override
    public ParseResult parse(String text, LocalDate today, int partySize) {
        if (!isConfigured()) {
            throw new IntentParseException("intent.llm.* not configured");
        }
        LocalDate effectiveToday = today == null ? LocalDate.now() : today;

        String requestBody = buildRequestBody(text, effectiveToday, partySize);
        String responseBody = send(requestBody);
        ObjectNode arguments = extractToolArguments(responseBody);
        IntentSpec spec = toIntentSpec(arguments, effectiveToday, partySize);

        return new ParseResult(spec, "llm");
    }


    private String buildRequestBody(String text, LocalDate today, int partySize) {
        List<String> hardKeys = new ArrayList<>();
        List<String> softKeys = new ArrayList<>();
        for (IntentProperties.ConstraintRule rule : config.constraints()) {
            if (rule == null || rule.key() == null) {
                continue;
            }
            (rule.isHard() ? hardKeys : softKeys).add(rule.key());
        }
        List<String> resourceTypes = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            resourceTypes.add(type.name());
        }

        String system = """
                You convert a natural-language %s booking request into structured fields.
                Today's date is %s (ISO-8601). The default party size if unstated is %d.
                Valid resourceType values (choose exactly one, or null if no %s is mentioned): %s
                Valid hard constraint keys (only use these, never invent a key): %s
                Valid soft constraint keys (only use these, never invent a key): %s
                Only put a key in hardConstraints or softConstraints if the text clearly implies it.
                day_from and day_to must be ISO-8601 dates (YYYY-MM-DD), with day_from <= day_to.
                timeOfDay must be one of ANY, MORNING, AFTERNOON, EVENING.
                Call the %s tool with your answer.
                """.formatted(
                domain.llmDomainDescription(), today, partySize, domain.llmDomainDescription(),
                resourceTypes, hardKeys, softKeys, TOOL_NAME);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0);

        ArrayNode messages = root.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", system);
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", text == null ? "" : text);

        ArrayNode tools = root.putArray("tools");
        ObjectNode tool = tools.addObject();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", TOOL_NAME);
        function.put("description", "Emit the parsed booking intent fields.");
        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("durationMin").put("type", "integer");
        properties.putObject("dayFrom").put("type", "string");
        properties.putObject("dayTo").put("type", "string");
        ObjectNode timeOfDay = properties.putObject("timeOfDay");
        timeOfDay.put("type", "string");
        ArrayNode timeEnum = timeOfDay.putArray("enum");
        for (TimeOfDay t : TimeOfDay.values()) {
            timeEnum.add(t.name());
        }
        properties.putObject("resourceType").put("type", "string");
        ObjectNode hardConstraintsNode = properties.putObject("hardConstraints");
        hardConstraintsNode.put("type", "array");
        hardConstraintsNode.putObject("items").put("type", "string");
        ObjectNode softConstraintsNode = properties.putObject("softConstraints");
        softConstraintsNode.put("type", "array");
        softConstraintsNode.putObject("items").put("type", "string");
        properties.putObject("partySize").put("type", "integer");

        ArrayNode required = parameters.putArray("required");
        required.add("durationMin");
        required.add("dayFrom");
        required.add("dayTo");
        required.add("timeOfDay");
        required.add("hardConstraints");
        required.add("softConstraints");
        required.add("partySize");

        ObjectNode toolChoice = root.putObject("tool_choice");
        toolChoice.put("type", "function");
        toolChoice.putObject("function").put("name", TOOL_NAME);

        return root.toString();
    }

    private String send(String requestBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IntentParseException("LLM endpoint returned status " + response.statusCode());
            }
            return response.body();
        } catch (HttpTimeoutException e) {
            throw new IntentParseException("LLM call timed out", e);
        } catch (IOException e) {
            throw new IntentParseException("LLM call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntentParseException("LLM call interrupted", e);
        }
    }


    private ObjectNode extractToolArguments(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode toolCalls = root.path("choices").path(0).path("message").path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                throw new IntentParseException("LLM response contained no tool call");
            }
            String argumentsJson = toolCalls.get(0).path("function").path("arguments").asString();
            JsonNode parsedArgs = objectMapper.readTree(argumentsJson);
            if (!(parsedArgs instanceof ObjectNode objectNode)) {
                throw new IntentParseException("LLM tool arguments were not a JSON object");
            }
            return objectNode;
        } catch (RuntimeException e) {
            if (e instanceof IntentParseException ipe) {
                throw ipe;
            }
            throw new IntentParseException("Could not parse LLM response JSON", e);
        }
    }

    private IntentSpec toIntentSpec(ObjectNode args, LocalDate today, int fallbackPartySize) {
        int durationMin = args.path("durationMin").asInt(60);
        if (durationMin <= 0) {
            throw new IntentParseException("LLM returned non-positive durationMin");
        }

        LocalDate dayFrom = parseDate(args.path("dayFrom").asString(null), today);
        LocalDate dayTo = parseDate(args.path("dayTo").asString(null), dayFrom);
        if (dayFrom.isAfter(dayTo)) {
            throw new IntentParseException("LLM returned dayFrom after dayTo");
        }

        TimeOfDay timeOfDay = parseTimeOfDay(args.path("timeOfDay").asString("ANY"));

        String resourceType = parseResourceType(args.path("resourceType").asString(null));

        Set<String> validHardKeys = new LinkedHashSet<>();
        Set<String> validSoftKeys = new LinkedHashSet<>();
        for (IntentProperties.ConstraintRule rule : config.constraints()) {
            if (rule == null || rule.key() == null) {
                continue;
            }
            if (rule.isHard()) {
                validHardKeys.add(rule.key());
            } else {
                validSoftKeys.add(rule.key());
            }
        }

        Set<String> returnedKeys = new LinkedHashSet<>();
        collectStrings(args.path("hardConstraints"), returnedKeys);
        collectStrings(args.path("softConstraints"), returnedKeys);

        List<String> hardConstraints = new ArrayList<>();
        List<String> softConstraints = new ArrayList<>();
        for (String key : returnedKeys) {
            if (validHardKeys.contains(key)) {
                hardConstraints.add(key);
            } else if (validSoftKeys.contains(key)) {
                softConstraints.add(key);
            }
        }

        int partySize = args.path("partySize").asInt(fallbackPartySize);
        if (partySize <= 0) {
            partySize = Math.max(1, fallbackPartySize);
        }

        return new IntentSpec(durationMin, dayFrom, dayTo, timeOfDay, hardConstraints, softConstraints, resourceType, partySize);
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            throw new IntentParseException("LLM omitted a required date field");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IntentParseException("LLM returned an unparseable date: " + value, e);
        }
    }

    private TimeOfDay parseTimeOfDay(String value) {
        if (value == null) {
            return TimeOfDay.ANY;
        }
        try {
            return TimeOfDay.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TimeOfDay.ANY;
        }
    }

    private String parseResourceType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ResourceType type : ResourceType.values()) {
            if (type.name().equals(normalized)) {
                return type.name();
            }
        }
        return null;
    }

    private void collectStrings(JsonNode arrayNode, Set<String> target) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        for (JsonNode node : arrayNode) {
            String text = node.asString(null);
            if (text != null && !text.isBlank()) {
                target.add(text.trim());
            }
        }
    }
}
