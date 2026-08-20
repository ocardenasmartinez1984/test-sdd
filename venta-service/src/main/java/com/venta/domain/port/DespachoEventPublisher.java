package com.venta.domain.port;

import com.venta.domain.event.DespachoRequestEvent;

public interface DespachoEventPublisher {
    void requestDespacho(DespachoRequestEvent event);
}
