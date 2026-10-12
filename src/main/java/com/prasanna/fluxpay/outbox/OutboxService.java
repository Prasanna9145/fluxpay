package com.prasanna.fluxpay.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxService {
    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private final OutboxEventRepository repository;
    private final JsonMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxService(OutboxEventRepository repository, JsonMapper objectMapper,
                         KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository; this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void enqueue(String topic, String key, Object payload) {
        try {
            repository.save(new OutboxEvent(topic, key, objectMapper.writeValueAsString(payload)));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Event payload could not be serialized", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:3000}")
    @Transactional
    public void publishDueEvents() {
        List<OutboxEvent> due = repository.findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(LocalDateTime.now());
        for (OutboxEvent event : due) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception exception) {
                event.markFailed(exception.getMessage());
                log.warn("Outbox event {} will retry after Kafka publishing failed", event.getId());
            }
        }
    }
}
