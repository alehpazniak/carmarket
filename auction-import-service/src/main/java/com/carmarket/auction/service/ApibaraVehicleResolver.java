package com.carmarket.auction.service;

import com.carmarket.auction.client.ApibaraClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a user pastes (a Copart/IAAI lot page URL, or just a lot number / VIN) into one Apibara
 * vehicle record (the whole response, raw JSON). urltodetails already returns the full record, so the happy path
 * costs one Apibara call; when it can't match the URL, the lot number from a Copart URL is looked up via
 * GET /vehicles/{lot}. The patterns are shared with {@link LotLookupService}, which matches them in our database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApibaraVehicleResolver {

    /** copart.com/lot/61747696/salvage-2021-mazda-... → 61747696 */
    private static final Pattern COPART_LOT = Pattern.compile("copart\\.[a-z.]+/lot/(\\d{5,10})", Pattern.CASE_INSENSITIVE);
    /**
     * iaai.com/VehicleDetail/46069832~US → 46069832. IAAI's item id, not the stock (lot) number: Apibara's record
     * of that car has lot_number 45571503 and the item id only in its image keys (imageKeys=46069832~SID~I1).
     */
    private static final Pattern IAAI_ITEM = Pattern.compile("iaai\\.com/(?:VehicleDetail/|Vehicle\\?itemID=)(\\d{5,10})",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern LOT_NUMBER = Pattern.compile("\\d{5,10}");
    private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}", Pattern.CASE_INSENSITIVE);

    private final ApibaraClient client;

    public Optional<JsonNode> resolve(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        // A bare lot number or VIN: GET /vehicles/{slugVin} accepts both
        if (isLotNumber(value) || isVin(value)) {
            return client.lookupVehicle(value.toUpperCase(Locale.ROOT));
        }
        Optional<JsonNode> byUrl = client.lookupUrl(value);
        if (byUrl.isPresent()) {
            return byUrl;
        }
        return copartLotNumber(value).flatMap(lot -> {
            log.info("Apibara urltodetails found nothing, falling back to Copart lot {}", lot);
            return client.lookupVehicle(lot);
        });
    }

    public static boolean isLotNumber(String value) {
        return LOT_NUMBER.matcher(value).matches();
    }

    public static boolean isVin(String value) {
        return VIN.matcher(value).matches();
    }

    public static Optional<String> copartLotNumber(String url) {
        Matcher m = COPART_LOT.matcher(url);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    public static Optional<String> iaaiItemId(String url) {
        Matcher m = IAAI_ITEM.matcher(url);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
