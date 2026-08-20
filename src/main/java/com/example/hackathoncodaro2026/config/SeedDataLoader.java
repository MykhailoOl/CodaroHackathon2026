package com.example.hackathoncodaro2026.config;

import com.example.hackathoncodaro2026.model.enums.ResourceType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeedDataLoader {

    private static final String SEED_PATH = "data/seed.yml";

    public SeedData load() {
        try (InputStream in = new ClassPathResource(SEED_PATH).getInputStream()) {
            Object root = new Yaml().load(in);
            Map<String, Object> map = asMap(root);
            List<SeedData.SeedFacility> facilities = new ArrayList<>();
            Object facilitiesRaw = map.get("facilities");
            if (facilitiesRaw != null) {
                for (Object item : asList(facilitiesRaw)) {
                    facilities.add(toFacility(asMap(item)));
                }
            }
            List<SeedData.SeedInventoryItem> inventory = new ArrayList<>();
            Object inventoryRaw = map.get("inventory");
            if (inventoryRaw != null) {
                for (Object item : asList(inventoryRaw)) {
                    inventory.add(toInventoryItem(asMap(item)));
                }
            }
            return new SeedData(facilities, inventory);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SEED_PATH, e);
        }
    }

    private SeedData.SeedFacility toFacility(Map<String, Object> facility) {
        String name = string(facility, "name");
        String description = string(facility, "description");
        String phone = string(facility, "phone");
        ResourceType coverType = resourceType(facility, "coverType");
        SeedData.SeedAddress address = toAddress(asMap(facility.get("address")));

        List<SeedData.SeedResource> resources = new ArrayList<>();
        Object resourcesRaw = facility.get("resources");
        if (resourcesRaw != null) {
            for (Object item : asList(resourcesRaw)) {
                resources.add(toResource(asMap(item), address));
            }
        }
        return new SeedData.SeedFacility(name, description, phone, address, coverType, resources);
    }

    private SeedData.SeedResource toResource(Map<String, Object> resource, SeedData.SeedAddress fallbackAddress) {
        String name = string(resource, "name");
        ResourceType type = resourceType(resource, "type");
        SeedData.SeedAddress address = fallbackAddress;
        if (resource.get("address") != null) {
            address = toAddress(asMap(resource.get("address")));
        }
        int capacity = integer(resource, "capacity", 1);
        return new SeedData.SeedResource(name, type, address, capacity);
    }

    private SeedData.SeedInventoryItem toInventoryItem(Map<String, Object> item) {
        String name = string(item, "name");
        BigDecimal price = new BigDecimal(string(item, "price"));
        ResourceType type = resourceType(item, "type");
        return new SeedData.SeedInventoryItem(name, price, type);
    }

    private SeedData.SeedAddress toAddress(Map<String, Object> address) {
        return new SeedData.SeedAddress(
                string(address, "street"),
                string(address, "buildingNumber"),
                string(address, "postalCode"),
                string(address, "district")
        );
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private ResourceType resourceType(Map<String, Object> map, String key) {
        String value = string(map, key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing resource type key '" + key + "' in " + SEED_PATH);
        }
        try {
            return ResourceType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Unknown resource type '" + value + "' in " + SEED_PATH + " (key '" + key + "')", e);
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        throw new IllegalStateException("Expected a map in " + SEED_PATH + " but got " + value.getClass().getSimpleName());
    }

    private List<Object> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        throw new IllegalStateException("Expected a list in " + SEED_PATH + " but got " + value.getClass().getSimpleName());
    }
}