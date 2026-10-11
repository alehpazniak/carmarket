package com.carmarket.importorder.service;

import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.exception.ApiException;
import com.carmarket.importorder.repository.ImportOrderRepository;
import com.carmarket.importorder.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Every dealer query goes through here, scoped by dealer_id = X-User-Id. Someone else's order is a
 * plain 404, the same as a missing one, so ids can't be probed. Admins may read any order, but
 * changes are limited to their own.
 */
@Component
@RequiredArgsConstructor
public class OrderLookup {

    private final ImportOrderRepository orders;

    public ImportOrder forRead(Actor actor, UUID orderId) {
        return (actor.admin() ? orders.findById(orderId) : orders.findByIdAndDealerId(orderId, actor.userId()))
            .orElseThrow(ApiException::notFound);
    }

    public ImportOrder forWrite(Actor actor, UUID orderId) {
        return orders.findByIdAndDealerId(orderId, actor.userId()).orElseThrow(ApiException::notFound);
    }
}
