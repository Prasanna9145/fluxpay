package com.prasanna.fluxpay.messaging;

import com.prasanna.fluxpay.event.PaymentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    @KafkaListener(topics = "payment.created", groupId = "${spring.kafka.consumer.group-id:fluxpay-group}", containerFactory = "kafkaListenerContainerFactory")
    public void consumePaymentCreatedEvent(PaymentCreatedEvent event) {
        log.info("Payment created event received: paymentId={}, userId={}, amount={} {}, createdAt={}",
            event.paymentId(), event.userId(), event.amount(), event.currency(), event.createdAt());
    }
}
