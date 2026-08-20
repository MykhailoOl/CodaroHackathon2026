package com.example.hackathoncodaro2026.config;

import com.example.hackathoncodaro2026.model.Address;
import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PricingMode;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.model.enums.VenueType;
import com.example.hackathoncodaro2026.repository.ArrangementExtraRepository;
import com.example.hackathoncodaro2026.repository.FuneralHomeRepository;
import com.example.hackathoncodaro2026.repository.ServiceVenueRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;

@Component
@Order(20)
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final FuneralHomeRepository funeralHomeRepository;
    private final ServiceVenueRepository serviceVenueRepository;
    private final ArrangementExtraRepository arrangementExtraRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserRepository userRepository,
            FuneralHomeRepository funeralHomeRepository,
            ServiceVenueRepository serviceVenueRepository,
            ArrangementExtraRepository arrangementExtraRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.funeralHomeRepository = funeralHomeRepository;
        this.serviceVenueRepository = serviceVenueRepository;
        this.arrangementExtraRepository = arrangementExtraRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureUser("admin", "admin@everrest.example", "Admin123!", "EverRest Administrator", "+48 22 100 2001", Role.ADMIN);
        ensureUser("manager", "manager@everrest.example", "Manager123!", "EverRest Manager", "+48 22 100 2002", Role.MANAGER);
        ensureUser("everrest_demo", "demo@everrest.example", "Demo123!", "Anna Kowalska", "+48 22 100 2003", Role.USER);
        seedHomes();
        seedExtras();
    }

    private void ensureUser(String username, String email, String password, String fullName, String phone, Role role) {
        // An account with no phone cannot complete an arrangement, and registration
        // leaves the field optional — so fill it in rather than leaving a demo account
        // that only fails at the last step.
        var existing = userRepository.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getPhone() == null || user.getPhone().isBlank()) {
                user.setPhone(phone);
                userRepository.save(user);
            }
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }

    private void seedHomes() {
        if (funeralHomeRepository.count() > 0) {
            return;
        }
        FuneralHome everRest = home(
                "EverRest Warsaw",
                "A quiet chapel house in Mokotów for burial, memorial, and farewell gatherings.",
                new Address("Puławska", "142", "02-670", "Mokotów"),
                "+48 22 310 1100",
                "/images/homes/everrest.jpg"
        );
        venue(everRest, "Willow Chapel", VenueType.CHAPEL, new Address("Puławska", "142A", "02-670", "Mokotów"), 80, 9, 17);
        venue(everRest, "Remembrance Hall", VenueType.CEREMONY_HALL, new Address("Puławska", "142B", "02-670", "Mokotów"), 120, 8, 18);
        venue(everRest, "Garden Pavilion", VenueType.MEMORIAL_GARDEN, new Address("Puławska", "144", "02-670", "Mokotów"), 60, 9, 17);

        FuneralHome peaceful = home(
                "Peaceful Passage",
                "A Żoliborz house for measured farewells, with a hall and reception rooms.",
                new Address("Mickiewicza", "27", "01-517", "Żoliborz"),
                "+48 22 310 1200",
                "/images/homes/peaceful.jpg"
        );
        venue(peaceful, "North Chapel", VenueType.CHAPEL, new Address("Mickiewicza", "27A", "01-517", "Żoliborz"), 70, 9, 17);
        venue(peaceful, "Quiet Reception", VenueType.RECEPTION_HALL, new Address("Mickiewicza", "27B", "01-517", "Żoliborz"), 90, 10, 18);

        FuneralHome gardens = home(
                "Warsaw Memorial Gardens",
                "Garden memorials and outdoor farewells beside Wilanów parkland.",
                new Address("Klimczaka", "5", "02-797", "Wilanów"),
                "+48 22 310 1300",
                "/images/homes/gardens.jpg"
        );
        venue(gardens, "Garden Memorial Pavilion", VenueType.MEMORIAL_GARDEN, new Address("Klimczaka", "5A", "02-797", "Wilanów"), 50, 9, 17);
        venue(gardens, "Lakeside Hall", VenueType.CEREMONY_HALL, new Address("Klimczaka", "5B", "02-797", "Wilanów"), 100, 8, 18);
        venue(gardens, "Wilanów Chapel", VenueType.CHAPEL, new Address("Klimczaka", "7", "02-797", "Wilanów"), 64, 9, 16);

        FuneralHome serenity = home(
                "Serenity Farewell House",
                "A Wola house with a cremation suite and a small chapel for family ceremonies.",
                new Address("Wolska", "88", "01-187", "Wola"),
                "+48 22 310 1400",
                "/images/homes/serenity.jpg"
        );
        venue(serenity, "Cremation Suite", VenueType.CREMATORIUM, new Address("Wolska", "88A", "01-187", "Wola"), 40, 8, 17);
        venue(serenity, "West Chapel", VenueType.CHAPEL, new Address("Wolska", "88B", "01-187", "Wola"), 55, 9, 17);
        venue(serenity, "Family Reception", VenueType.RECEPTION_HALL, new Address("Wolska", "90", "01-187", "Wola"), 70, 10, 18);

        FuneralHome harbor = home(
                "Quiet Harbor House",
                "A Praga house for memorials and farewells close to the Vistula.",
                new Address("Targowa", "44", "03-728", "Praga-Północ"),
                "+48 22 310 1500",
                "/images/homes/harbor.jpg"
        );
        venue(harbor, "River Chapel", VenueType.CHAPEL, new Address("Targowa", "44A", "03-728", "Praga-Północ"), 72, 9, 17);
        venue(harbor, "Harbor Hall", VenueType.CEREMONY_HALL, new Address("Targowa", "44B", "03-728", "Praga-Północ"), 110, 8, 18);

        FuneralHome linden = home(
                "Linden Rest Chapel",
                "An Ochota chapel house with a garden court for smaller gatherings.",
                new Address("Grójecka", "61", "02-301", "Ochota"),
                "+48 22 310 1600",
                "/images/homes/linden.jpg"
        );
        venue(linden, "Linden Chapel", VenueType.CHAPEL, new Address("Grójecka", "61A", "02-301", "Ochota"), 48, 9, 16);
        venue(linden, "Courtyard Garden", VenueType.MEMORIAL_GARDEN, new Address("Grójecka", "61B", "02-301", "Ochota"), 36, 9, 17);

        FuneralHome dawn = home(
                "Dawn Remembrance",
                "A Śródmieście townhouse for memorial services and receptions.",
                new Address("Marszałkowska", "28", "00-639", "Śródmieście"),
                "+48 22 310 1700",
                "/images/homes/dawn.jpg"
        );
        venue(dawn, "Town Chapel", VenueType.CHAPEL, new Address("Marszałkowska", "28A", "00-639", "Śródmieście"), 60, 9, 17);
        venue(dawn, "City Reception", VenueType.RECEPTION_HALL, new Address("Marszałkowska", "28B", "00-639", "Śródmieście"), 80, 10, 18);
        venue(dawn, "Dawn Hall", VenueType.CEREMONY_HALL, new Address("Marszałkowska", "30", "00-639", "Śródmieście"), 95, 8, 18);
    }

    private FuneralHome home(String name, String description, Address address, String phone, String imagePath) {
        FuneralHome home = new FuneralHome();
        home.setName(name);
        home.setDescription(description);
        home.setAddress(address);
        home.setPhone(phone);
        home.setEnabled(true);
        home.setImagePath(imagePath);
        return funeralHomeRepository.save(home);
    }

    private void venue(FuneralHome home, String name, VenueType type, Address address, int maxAttendees, int openHour, int closeHour) {
        ServiceVenue venue = new ServiceVenue();
        venue.setFuneralHome(home);
        venue.setName(name);
        venue.setType(type);
        venue.setAddress(address);
        venue.setMaxAttendees(maxAttendees);
        venue.setOpeningTime(LocalTime.of(openHour, 0));
        venue.setClosingTime(LocalTime.of(closeHour, 0));
        venue.setSlotDurationMinutes(30);
        venue.setEnabled(true);
        venue.setImagePath(type.getImagePath());
        serviceVenueRepository.save(venue);
    }

    private void seedExtras() {
        extra("Floral arrangement", PricingMode.FIXED, "450.00", null);
        extra("Memorial cards", PricingMode.PER_ATTENDEE, "12.00", null);
        extra("Obituary notice", PricingMode.FIXED, "180.00", null);
        extra("Ceremonial transport", PricingMode.FIXED, "650.00", null);
        extra("Family transport", PricingMode.FIXED, "320.00", null);
        extra("Reception catering", PricingMode.PER_ATTENDEE, "85.00", null);
        extra("Live music", PricingMode.FIXED, "900.00", null);
        extra("Ceremony livestream", PricingMode.FIXED, "280.00", null);
        extra("Urn selection", PricingMode.FIXED, "420.00", ServiceType.CREMATION_CEREMONY);
        extra("Venue decoration", PricingMode.FIXED, "240.00", null);
    }

    private void extra(String name, PricingMode mode, String amount, ServiceType required) {
        if (arrangementExtraRepository.existsByNameIgnoreCase(name)) {
            return;
        }
        ArrangementExtra extra = new ArrangementExtra();
        extra.setName(name);
        extra.setPricingMode(mode);
        extra.setAmount(new BigDecimal(amount));
        extra.setRequiredServiceType(required);
        extra.setEnabled(true);
        arrangementExtraRepository.save(extra);
    }
}
