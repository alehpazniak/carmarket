package com.carmarket.importorder.service;

import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.LotSource;
import com.carmarket.importorder.exception.ApiException;
import com.carmarket.importorder.validation.ContainerNumberValidator;
import com.carmarket.importorder.validation.VinValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Applies the editable order fields from a JSON object, shared by create and PATCH.
 * <p>
 * A field that is absent stays as it is, an explicit null clears it, and an unknown field is a 400.
 * That also keeps server-owned fields (status, dealer, access, timestamps) out of reach.
 */
final class OrderFields {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern HTTP_URL = Pattern.compile("^https?://[^\\s/?#]+\\.[^\\s/?#]+(?:[/?#]\\S*)?$", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    /** Serialized size cap for auctionSnapshot. */
    private static final int MAX_SNAPSHOT_CHARS = 200_000;

    private final ObjectMapper objectMapper;

    OrderFields(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param allowSnapshot auctionSnapshot is only accepted on create (it records the lot as it was then)
     */
    void apply(ImportOrder order, JsonNode body, boolean allowSnapshot) {
        if (body == null || !body.isObject()) throw ApiException.badRequest("Expected a JSON object");
        Iterator<Map.Entry<String, JsonNode>> fields = body.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            switch (name) {
                case "source" -> order.setSource(source(name, value));
                case "lotNumber" -> order.setLotNumber(text(name, value, 30));
                case "vin" -> order.setVin(vin(value));
                case "make" -> order.setMake(text(name, value, 60));
                case "model" -> order.setModel(text(name, value, 80));
                case "year" -> order.setYear(year(value));
                case "saleDate" -> order.setSaleDate(date(name, value));
                case "purchasePriceUsd" -> order.setPurchasePriceUsd(price(value));
                case "auctionLocation" -> order.setAuctionLocation(text(name, value, 200));
                case "auctionUrl" -> order.setAuctionUrl(httpUrl(name, value, 500));
                case "titleType" -> order.setTitleType(text(name, value, 120));
                case "titleExportOk" -> order.setTitleExportOk(nullableBool(name, value));
                case "titleRegistrationOk" -> order.setTitleRegistrationOk(nullableBool(name, value));
                case "auctionSnapshot" -> {
                    if (!allowSnapshot) throw ApiException.badRequest("auctionSnapshot can only be set on create");
                    order.setAuctionSnapshot(snapshot(value));
                }
                case "paymentDeadline" -> order.setPaymentDeadline(date(name, value));
                case "titleReceivedAt" -> order.setTitleReceivedAt(date(name, value));
                case "portOfLoading" -> order.setPortOfLoading(text(name, value, 120));
                case "shippingLine" -> order.setShippingLine(text(name, value, 120));
                case "containerNumber" -> order.setContainerNumber(containerNumber(value));
                case "bookingNumber" -> order.setBookingNumber(text(name, value, 60));
                case "billOfLading" -> order.setBillOfLading(text(name, value, 60));
                case "vesselName" -> order.setVesselName(text(name, value, 120));
                case "etd" -> order.setEtd(date(name, value));
                case "eta" -> order.setEta(date(name, value));
                case "destinationPort" -> order.setDestinationPort(text(name, value, 120));
                case "customsClearedAt" -> order.setCustomsClearedAt(date(name, value));
                case "clientName" -> order.setClientName(text(name, value, 200));
                case "clientEmail" -> order.setClientEmail(email(name, value));
                case "clientPhone" -> order.setClientPhone(text(name, value, 40));
                case "notifyClient" -> order.setNotifyClient(bool(name, value));
                case "dealerContactName" -> order.setDealerContactName(text(name, value, 200));
                case "dealerContactPhone" -> order.setDealerContactPhone(text(name, value, 40));
                case "dealerContactEmail" -> order.setDealerContactEmail(email(name, value));
                case "internalNotes" -> order.setInternalNotes(text(name, value, 20_000));
                default -> throw ApiException.badRequest("Unknown or read-only field: " + name);
            }
        }
    }

    private static boolean isNull(JsonNode value) {
        return value == null || value.isNull();
    }

    /** Trimmed text; blank becomes null. */
    static String text(String name, JsonNode value, int maxLength) {
        if (isNull(value)) return null;
        if (!value.isTextual()) throw ApiException.badRequest(name + ": expected text");
        String s = value.asText().trim();
        if (s.isEmpty()) return null;
        if (s.length() > maxLength) throw ApiException.badRequest(name + ": at most " + maxLength + " characters");
        return s;
    }

    /** An http(s) link; anything else (javascript:, data:, ...) is a 400, since the UI renders it as a link. */
    static String httpUrl(String name, JsonNode value, int maxLength) {
        String s = text(name, value, maxLength);
        if (s == null) return null;
        if (!HTTP_URL.matcher(s).matches()) throw ApiException.badRequest(name + ": expected an http(s) link");
        return s;
    }

    private static LotSource source(String name, JsonNode value) {
        String s = text(name, value, 10);
        if (s == null) throw ApiException.badRequest("source is required");
        try {
            return LotSource.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("source: one of COPART, IAAI, OTHER");
        }
    }

    private static String vin(JsonNode value) {
        if (isNull(value)) return null;
        if (!value.isTextual()) throw ApiException.badRequest("vin: expected text");
        String vin = VinValidator.normalize(value.asText());
        if (vin != null && !VinValidator.isValidFormat(vin)) {
            throw ApiException.badRequest("vin: 17 characters, digits and letters without I, O, Q");
        }
        return vin;
    }

    private static String containerNumber(JsonNode value) {
        if (isNull(value)) return null;
        if (!value.isTextual()) throw ApiException.badRequest("containerNumber: expected text");
        String number = ContainerNumberValidator.normalize(value.asText());
        if (number != null && !ContainerNumberValidator.isValid(number)) {
            throw ApiException.badRequest("containerNumber: not a valid ISO 6346 container number");
        }
        return number;
    }

    private static Integer year(JsonNode value) {
        if (isNull(value)) return null;
        if (!value.canConvertToInt() || !value.isIntegralNumber()) throw ApiException.badRequest("year: expected a number");
        int year = value.asInt();
        if (year < 1900 || year > 2100) throw ApiException.badRequest("year: between 1900 and 2100");
        return year;
    }

    private static LocalDate date(String name, JsonNode value) {
        String s = text(name, value, 30);
        if (s == null) return null;
        try {
            // Also accept a full timestamp (e.g. Apibara's auction_at) and keep its date part
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(name + ": expected a date (YYYY-MM-DD)");
        }
    }

    private static BigDecimal price(JsonNode value) {
        if (isNull(value)) return null;
        BigDecimal price;
        if (value.isNumber()) {
            price = value.decimalValue();
        } else if (value.isTextual()) {
            try {
                price = new BigDecimal(value.asText().trim());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("purchasePriceUsd: expected a number");
            }
        } else {
            throw ApiException.badRequest("purchasePriceUsd: expected a number");
        }
        if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0 || price.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("purchasePriceUsd: a non-negative amount with at most 2 decimals");
        }
        return price.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static Boolean nullableBool(String name, JsonNode value) {
        if (isNull(value)) return null;
        if (!value.isBoolean()) throw ApiException.badRequest(name + ": expected true or false");
        return value.asBoolean();
    }

    private static boolean bool(String name, JsonNode value) {
        Boolean b = nullableBool(name, value);
        if (b == null) throw ApiException.badRequest(name + ": expected true or false");
        return b;
    }

    private static String email(String name, JsonNode value) {
        String s = text(name, value, 255);
        if (s != null && !EMAIL.matcher(s).matches()) throw ApiException.badRequest(name + ": not a valid email address");
        return s;
    }

    private Map<String, Object> snapshot(JsonNode value) {
        if (isNull(value)) return null;
        if (!value.isObject()) throw ApiException.badRequest("auctionSnapshot: expected a JSON object");
        if (value.toString().length() > MAX_SNAPSHOT_CHARS) throw ApiException.badRequest("auctionSnapshot: too large");
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }
}
