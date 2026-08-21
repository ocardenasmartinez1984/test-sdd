package com.saga.orchestrator.application;

import com.saga.orchestrator.domain.event.*;
import com.saga.orchestrator.domain.model.SagaState;
import com.saga.orchestrator.domain.model.SagaStatus;
import com.saga.orchestrator.domain.model.SagaStep;
import com.saga.orchestrator.domain.repository.SagaStateRepository;
import com.saga.orchestrator.infrastructure.kafka.SagaProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestratorService {

    private final SagaStateRepository sagaStateRepository;
    private final SagaProducer sagaProducer;

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "startSagaFallback")
    public Mono<Void> startSaga(OrderCreatedEvent event) {
        log.info("Starting SAGA for order: {}", event.getOrderId());

        SagaState sagaState = SagaState.builder()
                .orderId(event.getOrderId())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .customerId(event.getCustomerId())
                .currentStep(SagaStep.STOCK_RESERVING)
                .status(SagaStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return sagaStateRepository.save(sagaState)
                .doOnSuccess(savedState -> {
                    StockReserveCommand command = StockReserveCommand.builder()
                            .sagaId(savedState.getId())
                            .orderId(savedState.getOrderId())
                            .productId(savedState.getProductId())
                            .quantity(savedState.getQuantity())
                            .build();

                    sagaProducer.sendStockReserveCommand(command);
                    log.info("Stock reserve command sent for saga: {}, order: {}", savedState.getId(), savedState.getOrderId());
                })
                .then();
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleStockReplyFallback")
    public Mono<Void> handleStockReply(StockReserveReply reply) {
        log.info("Handling stock reply for saga: {}, success: {}", reply.getSagaId(), reply.getSuccess());

        return sagaStateRepository.findById(reply.getSagaId())
                .switchIfEmpty(Mono.error(new RuntimeException("Saga not found: " + reply.getSagaId())))
                .flatMap(sagaState -> {
                    if (Boolean.TRUE.equals(reply.getSuccess())) {
                        sagaState.setCurrentStep(SagaStep.DESPACHO_CREATING);
                        sagaState.setUpdatedAt(LocalDateTime.now());

                        return sagaStateRepository.save(sagaState)
                                .doOnSuccess(savedState -> {
                                    DespachoCreateCommand command = DespachoCreateCommand.builder()
                                            .sagaId(savedState.getId())
                                            .orderId(savedState.getOrderId())
                                            .productId(savedState.getProductId())
                                            .quantity(savedState.getQuantity())
                                            .customerId(savedState.getCustomerId())
                                            .build();

                                    sagaProducer.sendDespachoCreateCommand(command);
                                    log.info("Despacho create command sent for saga: {}, order: {}", savedState.getId(), savedState.getOrderId());
                                });
                    } else {
                        sagaState.setCurrentStep(SagaStep.FAILED);
                        sagaState.setStatus(SagaStatus.FAILED);
                        sagaState.setFailureReason(reply.getReason());
                        sagaState.setUpdatedAt(LocalDateTime.now());

                        return sagaStateRepository.save(sagaState)
                                .doOnSuccess(savedState -> {
                                    OrderStatusUpdate statusUpdate = OrderStatusUpdate.builder()
                                            .orderId(savedState.getOrderId())
                                            .status("STOCK_FAILED")
                                            .failureReason(reply.getReason())
                                            .build();

                                    sagaProducer.sendOrderStatusUpdate(statusUpdate);
                                    log.info("Saga FAILED (stock) for order: {}. Reason: {}", savedState.getOrderId(), reply.getReason());
                                });
                    }
                })
                .then();
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleDespachoReplyFallback")
    public Mono<Void> handleDespachoReply(DespachoCreateReply reply) {
        log.info("Handling despacho reply for saga: {}, success: {}", reply.getSagaId(), reply.getSuccess());

        return sagaStateRepository.findById(reply.getSagaId())
                .switchIfEmpty(Mono.error(new RuntimeException("Saga not found: " + reply.getSagaId())))
                .flatMap(sagaState -> {
                    if (Boolean.TRUE.equals(reply.getSuccess())) {
                        sagaState.setCurrentStep(SagaStep.COMPLETED);
                        sagaState.setStatus(SagaStatus.COMPLETED);
                        sagaState.setTrackingNumber(reply.getTrackingNumber());
                        sagaState.setUpdatedAt(LocalDateTime.now());

                        return sagaStateRepository.save(sagaState)
                                .doOnSuccess(savedState -> {
                                    OrderStatusUpdate statusUpdate = OrderStatusUpdate.builder()
                                            .orderId(savedState.getOrderId())
                                            .status("DISPATCHING")
                                            .trackingNumber(reply.getTrackingNumber())
                                            .build();

                                    sagaProducer.sendOrderStatusUpdate(statusUpdate);
                                    log.info("Saga COMPLETED for order: {} (tracking: {})", savedState.getOrderId(), reply.getTrackingNumber());
                                });
                    } else {
                        sagaState.setCurrentStep(SagaStep.COMPENSATING);
                        sagaState.setStatus(SagaStatus.COMPENSATED);
                        sagaState.setFailureReason(reply.getReason());
                        sagaState.setUpdatedAt(LocalDateTime.now());

                        return sagaStateRepository.save(sagaState)
                                .doOnSuccess(savedState -> {
                                    StockCompensateCommand compensateCommand = StockCompensateCommand.builder()
                                            .sagaId(savedState.getId())
                                            .orderId(savedState.getOrderId())
                                            .productId(savedState.getProductId())
                                            .quantity(savedState.getQuantity())
                                            .build();

                                    sagaProducer.sendStockCompensateCommand(compensateCommand);

                                    OrderStatusUpdate statusUpdate = OrderStatusUpdate.builder()
                                            .orderId(savedState.getOrderId())
                                            .status("DISPATCH_FAILED")
                                            .failureReason(reply.getReason())
                                            .build();

                                    sagaProducer.sendOrderStatusUpdate(statusUpdate);
                                    log.info("Saga COMPENSATING for order: {}. Stock compensate sent. Reason: {}", savedState.getOrderId(), reply.getReason());
                                });
                    }
                })
                .then();
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleDespachoDeliveredFallback")
    public Mono<Void> handleDespachoDelivered(String orderId) {
        log.info("Handling despacho delivered for order: {}", orderId);

        return sagaStateRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Saga not found for order: " + orderId)))
                .flatMap(sagaState -> {
                    sagaState.setCurrentStep(SagaStep.COMPLETED);
                    sagaState.setStatus(SagaStatus.COMPLETED);
                    sagaState.setUpdatedAt(LocalDateTime.now());

                    return sagaStateRepository.save(sagaState)
                            .doOnSuccess(savedState -> {
                                OrderStatusUpdate statusUpdate = OrderStatusUpdate.builder()
                                        .orderId(savedState.getOrderId())
                                        .status("COMPLETED")
                                        .trackingNumber(savedState.getTrackingNumber())
                                        .build();

                                sagaProducer.sendOrderStatusUpdate(statusUpdate);
                                log.info("Order COMPLETED (delivered) for order: {}", savedState.getOrderId());
                            });
                })
                .then();
    }

    // Fallback methods
    private Mono<Void> startSagaFallback(OrderCreatedEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - startSaga failed for order: {}. Error: {}", event.getOrderId(), t.getMessage());
        return Mono.empty();
    }

    private Mono<Void> handleStockReplyFallback(StockReserveReply reply, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - handleStockReply failed for saga: {}. Error: {}", reply.getSagaId(), t.getMessage());
        return Mono.empty();
    }

    private Mono<Void> handleDespachoReplyFallback(DespachoCreateReply reply, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - handleDespachoReply failed for saga: {}. Error: {}", reply.getSagaId(), t.getMessage());
        return Mono.empty();
    }

    private Mono<Void> handleDespachoDeliveredFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - handleDespachoDelivered failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.empty();
    }
}
