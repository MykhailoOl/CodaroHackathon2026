package com.example.hackathoncodaro2026.repository;

import com.example.hackathoncodaro2026.model.FuneralHome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FuneralHomeRepository extends JpaRepository<FuneralHome, Long> {

    List<FuneralHome> findByEnabledTrueOrderByNameAsc();

    Optional<FuneralHome> findByIdAndEnabledTrue(Long id);

    boolean existsByNameIgnoreCase(String name);
}
