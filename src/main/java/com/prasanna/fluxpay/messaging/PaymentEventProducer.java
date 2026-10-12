package com.prasanna.fluxpay.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.prasanna.fluxpay.event.PaymentCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Compatibility facade; PaymentService normally uses the transactional outbox directly. */
@Service
public class PaymentEventProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper objectMapper;
    public PaymentEventProducer(KafkaTemplate<String, String> kafkaTemplate, JsonMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate; this.objectMapper = objectMapper;
    }
    public void publishPaymentCreated(PaymentCreatedEvent event) {
        try { kafkaTemplate.send("payment.created", event.paymentId().toString(), objectMapper.writeValueAsString(event)); }
        catch (JacksonException e) { throw new IllegalArgumentException("Payment event could not be serialized", e); }
    }
}
