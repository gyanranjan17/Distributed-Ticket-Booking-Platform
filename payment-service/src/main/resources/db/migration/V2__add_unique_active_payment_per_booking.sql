CREATE UNIQUE INDEX uk_active_payment_per_booking
    ON payments (booking_id)
    WHERE status IN ('PENDING', 'SUCCESS');