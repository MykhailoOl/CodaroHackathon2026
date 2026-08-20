package com.example.hackathoncodaro2026.model.enums;

public enum VenueType {
    CHAPEL("Chapel", "/images/venues/chapel.jpg"),
    CEREMONY_HALL("Ceremony hall", "/images/venues/hall.jpg"),
    CREMATORIUM("Cremation suite", "/images/venues/cremation.jpg"),
    MEMORIAL_GARDEN("Memorial garden", "/images/venues/garden.jpg"),
    RECEPTION_HALL("Reception hall", "/images/venues/reception.jpg");

    private final String label;
    private final String imagePath;

    VenueType(String label, String imagePath) {
        this.label = label;
        this.imagePath = imagePath;
    }

    public String getLabel() {
        return label;
    }

    public String getImagePath() {
        return imagePath;
    }
}
