package com.ledgersaas.backend.model.event;

import com.ledgersaas.backend.model.entity.Invoice;
import com.ledgersaas.backend.model.entity.Subscription;

public record PaymentSuccessEvent(Subscription subscription, Invoice invoice) {
}
