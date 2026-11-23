package com.flashseat.booking.scheduler;

import com.flashseat.booking.entity.BookingStatus;
import com.flashseat.booking.repository.BookingRepository;
import com.flashseat.booking.service.BookingExpirationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final BookingExpirationService expirationService;

    @Scheduled(fixedDelay = 30000)
    public void expirePendingBookings() {

        List<Long> expiredBookingIds =
                bookingRepository.findExpiredBookingIds(
                        BookingStatus.PENDING,
                        LocalDateTime.now()
                );

        for (Long bookingId : expiredBookingIds) {
            try {
                boolean expired =
                        expirationService.expireBooking(bookingId);

                if (expired) {
                    log.info("Expired booking: {}", bookingId);
                }

            } catch (Exception ex) {
                log.error(
                        "Failed to expire booking: {}",
                        bookingId,
                        ex
                );
            }
        }
    }
}
