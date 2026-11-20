package com.flashseat.booking.repository;

import com.flashseat.booking.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {
    List<ShowSeat> findByShow_Id(Long showId);

    Optional<ShowSeat> findByShow_IdAndSeat_Id(
      Long showId,
      Long seatId
    );
}
