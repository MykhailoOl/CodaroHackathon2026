package com.example.hackathoncodaro2026.repository;

import com.example.hackathoncodaro2026.model.ServiceVenue;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceVenueRepository extends JpaRepository<ServiceVenue, Long> {

    List<ServiceVenue> findByFuneralHome_IdAndEnabledTrueOrderByNameAsc(Long funeralHomeId);

    @Query("""
            SELECT v FROM ServiceVenue v
            JOIN FETCH v.funeralHome
            WHERE v.id = :id AND v.enabled = true AND v.funeralHome.enabled = true
            """)
    Optional<ServiceVenue> findEnabledWithHome(@Param("id") Long id);

    List<ServiceVenue> findByEnabledTrueOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "4000"))
    @Query("SELECT v FROM ServiceVenue v WHERE v.id = :id")
    Optional<ServiceVenue> lockById(@Param("id") Long id);
}
