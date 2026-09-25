package com.carmarket.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of PUT /users/me/contact — the user's own phone and address.
 * Phone, city and street are required; house number is optional (blank clears it).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactInfoRequest {

    @NotBlank(message = "Phone number is required")
    // Blank passes here so an empty phone reports only "required", not a format error too.
    @Pattern(regexp = "^\\s*$|^\\+?[0-9 ()-]{7,20}$",
        message = "Phone number may contain only digits, spaces, (), - and a leading +, 7-20 characters")
    private String phoneNumber;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotBlank(message = "Street is required")
    @Size(max = 100, message = "Street must not exceed 100 characters")
    private String street;

    @Size(max = 20, message = "House number must not exceed 20 characters")
    private String houseNumber;
}
