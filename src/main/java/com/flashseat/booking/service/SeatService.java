
package com.flashseat.booking.service;

import com.flashseat.booking.dto.AddShowSeatRequest;
import com.flashseat.booking.dto.CreateSeatRequest;
import com.flashseat.booking.dto.SeatResponse;
import com.flashseat.booking.dto.ShowSeatResponse;
import com.flashseat.booking.entity.*;
import com.flashseat.booking.exception.ResourceNotFoundException;
import com.flashseat.booking.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public SeatResponse createSeat(CreateSeatRequest request) {

        Seat seat = new Seat();
        seat.setSeatNumber(request.getSeatNumber());
        seat.setRowLabel(request.getRowLabel());

        Seat saved = seatRepository.save(seat);

        return new SeatResponse(
                saved.getId(),
                saved.getSeatNumber(),
                saved.getRowLabel()
        );
    }

    @Transactional
    public ShowSeatResponse addSeatToShow(
            Long showId,
            AddShowSeatRequest request) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Show not found: " + showId));

        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Seat not found: " + request.getSeatId()));

        if (showSeatRepository
                .findByShow_IdAndSeat_Id(showId, seat.getId())
                .isPresent()) {

            throw new IllegalArgumentException(
                    "Seat is already assigned to this show");
        }

        ShowSeat showSeat = new ShowSeat();
        showSeat.setShow(show);
        showSeat.setSeat(seat);
        showSeat.setPrice(request.getPrice());
        showSeat.setStatus(SeatStatus.AVAILABLE);

        ShowSeat saved = showSeatRepository.save(showSeat);

        return new ShowSeatResponse(
                saved.getId(),
                show.getId(),
                seat.getId(),
                seat.getSeatNumber(),
                seat.getRowLabel(),
                saved.getStatus(),
                saved.getPrice()
        );
    }


    @Transactional(readOnly = true)
    public List<ShowSeatResponse> getSeatsByShowId(Long showId) {

        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException(
                    "Show not found: " + showId
            );
        }

        return showSeatRepository.findByShow_Id(showId)
                .stream()
                .map(showSeat -> new ShowSeatResponse(
                        showSeat.getId(),
                        showSeat.getShow().getId(),
                        showSeat.getSeat().getId(),
                        showSeat.getSeat().getSeatNumber(),
                        showSeat.getSeat().getRowLabel(),
                        showSeat.getStatus(),
                        showSeat.getPrice()
                ))
                .toList();
    }

}
