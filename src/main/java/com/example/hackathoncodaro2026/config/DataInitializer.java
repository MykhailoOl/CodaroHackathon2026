package com.example.hackathoncodaro2026.config;

import com.example.hackathoncodaro2026.model.Address;
import com.example.hackathoncodaro2026.model.Facility;
import com.example.hackathoncodaro2026.model.InventoryItem;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.FacilityRepository;
import com.example.hackathoncodaro2026.repository.InventoryItemRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DataInitializer implements CommandLineRunner {

    private static final LocalTime OPEN = LocalTime.of(7, 0);
    private static final LocalTime CLOSE = LocalTime.of(22, 0);

    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final SportResourceRepository sportResourceRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedDataLoader seedDataLoader;

    public DataInitializer(
            UserRepository userRepository,
            FacilityRepository facilityRepository,
            SportResourceRepository sportResourceRepository,
            ReservationRepository reservationRepository,
            InventoryItemRepository inventoryItemRepository,
            PasswordEncoder passwordEncoder,
            SeedDataLoader seedDataLoader
    ) {
        this.userRepository = userRepository;
        this.facilityRepository = facilityRepository;
        this.sportResourceRepository = sportResourceRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedDataLoader = seedDataLoader;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsernameIgnoreCase("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@sportsfacility.local");
            admin.setPassword(passwordEncoder.encode("Admin123!"));
            admin.setFullName("Facility Administrator");
            admin.setRole(Role.ADMIN);
            admin.setPhone("+48 22 621 00 01");
            admin.setEnabled(true);
            userRepository.save(admin);
        } else {
            userRepository.findByUsernameIgnoreCase("admin").ifPresent(admin -> {
                if (admin.getPhone() == null || admin.getPhone().isBlank()) {
                    admin.setPhone("+48 22 621 00 01");
                    userRepository.save(admin);
                }
            });
        }
        if (!userRepository.existsByUsernameIgnoreCase("manager")) {
            User manager = new User();
            manager.setUsername("manager");
            manager.setEmail("manager@sportsfacility.local");
            manager.setPassword(passwordEncoder.encode("Manager123!"));
            manager.setFullName("Court Manager");
            manager.setRole(Role.MANAGER);
            manager.setPhone("+48 22 621 00 02");
            manager.setEnabled(true);
            userRepository.save(manager);
        }
        if (facilityRepository.count() == 0) {
            seedNetwork();
        }
        backfillImagePaths();
        backfillPartySizes();
        backfillSwimCapacities();
        backfillLessonPartySizes();
        backfillHourlyPrices();
        backfillLessonPrices();
        seedInventory();
        backfillReservationFields();
    }

    private void seedNetwork() {
        SeedData seed = seedDataLoader.load();
        for (SeedData.SeedFacility seedFacility : seed.facilities()) {
            saveSeedFacility(seedFacility);
        }
    }

    private void saveSeedFacility(SeedData.SeedFacility seedFacility) {
        Facility facility = saveFacility(
                seedFacility.name(),
                seedFacility.description(),
                seedFacility.phone(),
                toAddress(seedFacility.address()),
                seedFacility.coverType()
        );
        List<SportResource> resources = new ArrayList<>();
        for (SeedData.SeedResource seedResource : seedFacility.resources()) {
            resources.add(resource(facility, seedResource.name(), seedResource.type(),
                    toAddress(seedResource.address()), seedResource.capacity()));
        }
        saveResources(resources);
    }

    private Address toAddress(SeedData.SeedAddress seedAddress) {
        return new Address(
                seedAddress.street(),
                seedAddress.buildingNumber(),
                seedAddress.postalCode(),
                seedAddress.district()
        );
    }

    private Facility saveFacility(String name, String description, String phone, Address address, ResourceType coverType) {
        Facility facility = new Facility();
        facility.setName(name);
        facility.setDescription(description);
        facility.setPhone(phone);
        facility.setAddress(address);
        facility.setEnabled(true);
        facility.setImagePath(coverType.getImagePath());
        return facilityRepository.save(facility);
    }

    private SportResource resource(Facility facility, String name, ResourceType type, Address address, int capacity) {
        SportResource resource = new SportResource();
        resource.setFacility(facility);
        resource.setName(name);
        resource.setType(type);
        resource.setAddress(address);
        resource.setCapacity(capacity);
        resource.setSlotDurationMinutes(60);
        resource.setOpeningTime(OPEN);
        resource.setClosingTime(CLOSE);
        resource.setEnabled(true);
        resource.setImagePath(type.getImagePath());
        resource.setMinPartySize(type.getMinPartySize());
        resource.setMaxPartySize(type.getMaxPartySize());
        applyLessonPartyRange(resource);
        resource.setBaseHourlyPrice(type.getBaseHourlyPrice());
        resource.setLessonHourlyPrice(type.getLessonHourlyPrice());
        return resource;
    }

    private void saveResources(List<SportResource> resources) {
        sportResourceRepository.saveAll(resources);
    }

    private void backfillImagePaths() {
        for (SportResource resource : sportResourceRepository.findAll()) {
            if (resource.getImagePath() == null || resource.getImagePath().isBlank()) {
                resource.setImagePath(resource.getType().getImagePath());
                sportResourceRepository.save(resource);
            }
        }
        for (Facility facility : facilityRepository.findAll()) {
            if (facility.getImagePath() == null || facility.getImagePath().isBlank()) {
                List<SportResource> resources = sportResourceRepository.findByFacility_IdAndEnabledTrueOrderByNameAsc(facility.getId());
                if (!resources.isEmpty()) {
                    facility.setImagePath(resources.getFirst().getType().getImagePath());
                } else {
                    facility.setImagePath(ResourceType.TENNIS.getImagePath());
                }
                facilityRepository.save(facility);
            }
        }
    }

    private void backfillPartySizes() {
        for (SportResource resource : sportResourceRepository.findAll()) {
            if (needsPartySizeBackfill(resource)) {
                resource.setMinPartySize(resource.getType().getMinPartySize());
                resource.setMaxPartySize(resource.getType().getMaxPartySize());
                sportResourceRepository.save(resource);
            }
        }
    }

    private boolean needsPartySizeBackfill(SportResource resource) {
        int min = resource.getMinPartySize();
        int max = resource.getMaxPartySize();
        if (min < 1 || max < 1 || max < min) {
            return true;
        }
        return min == 1 && max == 1 && resource.getType().getMaxPartySize() > 1;
    }

    private void backfillSwimCapacities() {
        for (SportResource resource : sportResourceRepository.findAll()) {
            if (resource.getType() != ResourceType.SWIMMING || resource.getCapacity() > 1) {
                continue;
            }
            String name = resource.getName() == null ? "" : resource.getName();
            resource.setCapacity(name.contains("2") ? 6 : 8);
            sportResourceRepository.save(resource);
        }
    }

    private void backfillLessonPartySizes() {
        for (SportResource resource : sportResourceRepository.findAll()) {
            int min = resource.getLessonMinPartySize();
            int max = resource.getLessonMaxPartySize();
            applyLessonPartyRange(resource);
            if (resource.getLessonMinPartySize() != min || resource.getLessonMaxPartySize() != max) {
                sportResourceRepository.save(resource);
            }
        }
    }

    private void applyLessonPartyRange(SportResource resource) {
        if (resource.getMinPartySize() == 1 && resource.getMaxPartySize() == 1 && resource.getCapacity() > 1) {
            resource.setLessonMinPartySize(2);
            resource.setLessonMaxPartySize(resource.getCapacity());
            return;
        }
        resource.setLessonMinPartySize(1);
        resource.setLessonMaxPartySize(1);
    }

    private void backfillHourlyPrices() {
        for (SportResource resource : sportResourceRepository.findAll()) {
            if (resource.getBaseHourlyPrice() == null || resource.getBaseHourlyPrice().compareTo(BigDecimal.ZERO) <= 0) {
                resource.setBaseHourlyPrice(resource.getType().getBaseHourlyPrice());
                sportResourceRepository.save(resource);
            }
        }
    }

    private void backfillLessonPrices() {
        for (SportResource resource : sportResourceRepository.findAll()) {
            if (resource.getLessonHourlyPrice() == null || resource.getLessonHourlyPrice().compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal lesson = resource.getType().getLessonHourlyPrice();
                if (lesson != null && lesson.compareTo(BigDecimal.ZERO) > 0) {
                    resource.setLessonHourlyPrice(lesson);
                    sportResourceRepository.save(resource);
                }
            }
        }
    }

    private void seedInventory() {
        for (SeedData.SeedInventoryItem item : seedDataLoader.load().inventory()) {
            seedItem(item.name(), item.price().toString(), item.type());
        }
    }

    private void seedItem(String name, String price, ResourceType type) {
        if (inventoryItemRepository.existsByNameIgnoreCaseAndResourceType(name, type)) {
            return;
        }
        InventoryItem item = new InventoryItem();
        item.setName(name);
        item.setPricePerPerson(new BigDecimal(price));
        item.setResourceType(type);
        item.setEnabled(true);
        inventoryItemRepository.save(item);
    }

    private void backfillReservationFields() {
        for (Reservation reservation : reservationRepository.findAll()) {
            boolean changed = false;
            if (reservation.getPartySize() < 1) {
                reservation.setPartySize(1);
                changed = true;
            }
            if (reservation.getPaymentMethod() == null) {
                reservation.setPaymentMethod(PaymentMethod.CASH);
                changed = true;
            }
            if (reservation.getTotalAmount() == null) {
                reservation.setTotalAmount(BigDecimal.ZERO.setScale(2));
                changed = true;
            }
            if (reservation.getKind() == null) {
                reservation.setKind(ReservationKind.STANDARD);
                changed = true;
            }
            if (reservation.getOccupancyUnits() < 1) {
                reservation.setOccupancyUnits(1);
                changed = true;
            }
            if (changed) {
                reservationRepository.save(reservation);
            }
        }
    }
}
