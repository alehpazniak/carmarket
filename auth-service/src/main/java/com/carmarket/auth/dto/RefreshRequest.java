package com.carmarket.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    @NotBlank
    @JsonProperty("refresh_token")
    private String refreshToken;
}
