package com.flashseat.booking.repository;

import com.flashseat.booking.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import com.flashseat.booking.entity.SeatStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {
    List<ShowSeat> findByShow_Id(Long showId);

    Optional<ShowSeat> findByShow_IdAndSeat_Id(
      Long showId,
      Long seatId
    );

    @Modifying
    @Query("""
    UPDATE ShowSeat ss
    SET ss.status = :newStatus
    WHERE ss.id = :showSeatId
      AND ss.status = :expectedStatus
    """)
    int updateSeatStatusIfMatch(
            @Param("showSeatId") Long showSeatId,
            @Param("expectedStatus") SeatStatus expectedStatus,
            @Param("newStatus") SeatStatus newStatus
    );

}
