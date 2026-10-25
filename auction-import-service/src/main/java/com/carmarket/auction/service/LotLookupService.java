package com.carmarket.auction.service;

import com.carmarket.auction.dto.LotLookupResponse;
import com.carmarket.auction.dto.LotLookupResponse.Kind;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * "Numer lota, VIN lub link" on /aukcje, in our database only (no Apibara request): a lot number (any platform),
 * a VIN (exact; Copart's masked VINs won't match, which is normal), a Copart link (its lot number) or an IAAI link
 * (its item id, matched through the image keys, since IAAI links don't carry the stock number).
 */
@Service
@RequiredArgsConstructor
public class LotLookupService {

    private static final Pattern LINK = Pattern.compile("^(https?://|www\\.)|\\.(com|net)/", Pattern.CASE_INSENSITIVE);

    /** LIVE first, then the latest auction (none last) */
    private static final Comparator<AuctionLot> BEST_FIRST = Comparator
        .comparing((AuctionLot lot) -> lot.getStatus() != AuctionLot.LotStatus.LIVE)
        .thenComparing(AuctionLot::getAuctionDate, Comparator.nullsLast(Comparator.<Instant>reverseOrder()));

    private final AuctionLotRepository lots;

    @Transactional(readOnly = true)
    public LotLookupResponse lookup(String query) {
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) {
            return new LotLookupResponse(Kind.UNKNOWN, List.of(), null);
        }
        if (ApibaraVehicleResolver.isLotNumber(value)) {
            return found(Kind.LOT_NUMBER, lots.findByLotNumber(value), value);
        }
        if (ApibaraVehicleResolver.isVin(value)) {
            String vin = value.toUpperCase(Locale.ROOT);
            return found(Kind.VIN, lots.findByVin(vin), vin);
        }
        Optional<String> copartLot = ApibaraVehicleResolver.copartLotNumber(value);
        if (copartLot.isPresent()) {
            return found(Kind.COPART_URL, lots.findByLotNumber(copartLot.get()).stream()
                .filter(lot -> lot.getSource() == AuctionLot.AuctionSource.COPART).toList(), null);
        }
        Optional<String> iaaiItem = ApibaraVehicleResolver.iaaiItemId(value);
        if (iaaiItem.isPresent()) {
            return found(Kind.IAAI_URL, lots.findIaaiByImageKey("%imageKeys=" + iaaiItem.get() + "~%"), null);
        }
        return new LotLookupResponse(LINK.matcher(value).find() ? Kind.URL : Kind.UNKNOWN, List.of(), null);
    }

    private static LotLookupResponse found(Kind kind, List<AuctionLot> matches, String identifier) {
        List<UUID> ids = matches.stream().sorted(BEST_FIRST).map(AuctionLot::getId).toList();
        return new LotLookupResponse(kind, ids, identifier);
    }
}
