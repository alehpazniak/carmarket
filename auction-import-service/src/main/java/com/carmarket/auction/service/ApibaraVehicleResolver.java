package com.carmarket.auction.service;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraVehicleDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a dealer pastes (a Copart/IAAI lot page URL, or just a lot number / VIN) into one Apibara
 * vehicle record. urltodetails already returns the full record, so the happy path costs one Apibara call;
 * when it can't match the URL, the lot number from a Copart URL is looked up via GET /vehicles/{lot}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApibaraVehicleResolver {

    /** copart.com/lot/61747696/salvage-2021-mazda-... → 61747696 */
    private static final Pattern COPART_LOT = Pattern.compile("copart\\.[a-z.]+/lot/(\\d{5,10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOT_NUMBER = Pattern.compile("\\d{5,10}");
    private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}", Pattern.CASE_INSENSITIVE);

    private final ApibaraClient client;

    public Optional<ApibaraVehicleDetailResponse> resolve(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        // A bare lot number or VIN: GET /vehicles/{slugVin} accepts both
        if (LOT_NUMBER.matcher(value).matches() || VIN.matcher(value).matches()) {
            return found(client.getVehicle(value.toUpperCase()));
        }
        Optional<ApibaraVehicleDetailResponse> byUrl = found(client.resolveFromUrl(value));
        if (byUrl.isPresent()) {
            return byUrl;
        }
        return copartLotNumber(value).flatMap(lot -> {
            log.info("Apibara urltodetails found nothing, falling back to Copart lot {}", lot);
            return found(client.getVehicle(lot));
        });
    }

    static Optional<String> copartLotNumber(String url) {
        Matcher m = COPART_LOT.matcher(url);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static Optional<ApibaraVehicleDetailResponse> found(ApibaraVehicleDetailResponse response) {
        return response != null && response.data() != null ? Optional.of(response) : Optional.empty();
    }
}
