package com.example.hackathoncodaro2026.repository;

import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            SELECT COALESCE(SUM(r.occupancyUnits), 0) FROM Reservation r
            WHERE r.resource.id = :resourceId
              AND r.status IN :statuses
              AND r.startAt < :endAt
              AND r.endAt > :startAt
            """)
    long countOverlapping(
            @Param("resourceId") Long resourceId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
            SELECT COALESCE(SUM(r.occupancyUnits), 0) FROM Reservation r
            WHERE r.resource.id = :resourceId
              AND r.status IN :statuses
              AND r.startAt < :endAt
              AND r.endAt > :startAt
              AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    long countOverlappingExcluding(
            @Param("resourceId") Long resourceId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            SELECT DISTINCT r FROM Reservation r
            JOIN FETCH r.resource res
            JOIN FETCH res.facility
            LEFT JOIN FETCH r.extras ex
            LEFT JOIN FETCH ex.item
            LEFT JOIN FETCH r.coach
            WHERE r.user.id = :userId
            ORDER BY r.startAt DESC
            """)
    List<Reservation> findByUserIdWithDetails(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT r FROM Reservation r
            JOIN FETCH r.resource res
            JOIN FETCH res.facility
            JOIN FETCH r.user
            LEFT JOIN FETCH r.extras ex
            LEFT JOIN FETCH ex.item
            LEFT JOIN FETCH r.coach
            ORDER BY r.startAt DESC
            """)
    List<Reservation> findAllWithDetails();

    @Query("""
            SELECT DISTINCT r FROM Reservation r
            JOIN FETCH r.resource res
            JOIN FETCH res.facility
            JOIN FETCH r.user
            LEFT JOIN FETCH r.extras ex
            LEFT JOIN FETCH ex.item
            LEFT JOIN FETCH r.coach
            WHERE r.id = :id
            """)
    Optional<Reservation> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "4000"))
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> lockById(@Param("id") Long id);

    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.resource res
            WHERE r.status IN :statuses
              AND r.startAt < :endAt
              AND r.endAt > :startAt
            """)
    List<Reservation> findOccupyingOverlapping(
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    long countByUserAndStatusInAndStartAtAfter(User user, Collection<ReservationStatus> statuses, LocalDateTime startAt);

    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.coach.id = :coachId
              AND r.status IN :statuses
              AND r.startAt < :endAt
              AND r.endAt > :startAt
            """)
    long countCoachOverlapping(
            @Param("coachId") Long coachId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.coach.id = :coachId
              AND r.status IN :statuses
              AND r.startAt < :endAt
              AND r.endAt > :startAt
              AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    long countCoachOverlappingExcluding(
            @Param("coachId") Long coachId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            SELECT DISTINCT r FROM Reservation r
            JOIN FETCH r.resource res
            JOIN FETCH res.facility
            JOIN FETCH r.user
            LEFT JOIN FETCH r.extras ex
            LEFT JOIN FETCH ex.item
            LEFT JOIN FETCH r.coach
            WHERE r.coach.id = :coachId
            ORDER BY r.startAt DESC
            """)
    List<Reservation> findByCoachIdWithDetails(@Param("coachId") Long coachId);

    long countByCoachAndStatusInAndStartAtAfter(User coach, Collection<ReservationStatus> statuses, LocalDateTime startAt);

    @Query("""
            SELECT DISTINCT r FROM Reservation r
            JOIN FETCH r.resource res
            JOIN FETCH res.facility
            JOIN FETCH r.user
            LEFT JOIN FETCH r.extras ex
            LEFT JOIN FETCH ex.item
            LEFT JOIN FETCH r.coach
            WHERE r.startAt >= :dayStart
              AND r.startAt < :dayEnd
              AND r.status IN :statuses
            ORDER BY r.startAt ASC
            """)
    List<Reservation> findQueueForDate(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("statuses") Collection<ReservationStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Reservation r WHERE r.endAt < :cutoff")
    int deleteEndedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.resource res
            JOIN FETCH res.facility
            JOIN FETCH r.user
            WHERE r.inviteToken = :token
            """)
    Optional<Reservation> findByInviteToken(@Param("token") String token);
}
