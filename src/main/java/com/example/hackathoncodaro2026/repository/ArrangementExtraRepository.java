package com.example.hackathoncodaro2026.repository;

import com.example.hackathoncodaro2026.model.ArrangementExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArrangementExtraRepository extends JpaRepository<ArrangementExtra, Long> {

    List<ArrangementExtra> findByEnabledTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
