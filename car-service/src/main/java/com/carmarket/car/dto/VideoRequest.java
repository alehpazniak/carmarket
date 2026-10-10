package com.carmarket.car.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A YouTube, TikTok or Instagram link to attach to a listing. */
public record VideoRequest(@NotBlank @Size(max = 500) String url) {
}
