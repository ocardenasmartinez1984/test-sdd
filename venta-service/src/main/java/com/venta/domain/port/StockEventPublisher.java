package com.venta.domain.port;

import com.venta.domain.event.StockReserveEvent;

public interface StockEventPublisher {
    void reserveStock(StockReserveEvent event);
    void compensateStock(StockReserveEvent event);
}
