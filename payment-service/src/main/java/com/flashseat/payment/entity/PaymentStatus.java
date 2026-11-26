package com.flashseat.payment.entity;

public enum PaymentStatus {

    PENDING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean canTransitionTo(PaymentStatus targetStatus) {

        return this == PENDING
                && (targetStatus == SUCCESS
                || targetStatus == FAILED);
    }
}
