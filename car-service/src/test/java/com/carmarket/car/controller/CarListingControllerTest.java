package com.carmarket.car.controller;

import com.carmarket.car.config.SecurityConfigTest;
import com.carmarket.car.dto.CarListingRequest;
import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.entity.FuelType;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.entity.Transmission;
import com.carmarket.car.service.CarListingService;
import com.carmarket.car.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CarListingController.class)
@Import(SecurityConfigTest.class)
@DisplayName("CarListingController Tests")
class CarListingControllerTest {

    @MockBean
    private CarListingService carListingService;

    @MockBean
    private S3Service s3Service;

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;
    private UUID testUserId;
    private UUID testListingId;
    private CarListingResponse testListing;
    private CarListingRequest testRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testUserId = UUID.randomUUID();
        testListingId = UUID.randomUUID();

        testRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(25000.00))
            .mileage(15000)
            .fuelType(FuelType.PETROL)
            .transmission(Transmission.AUTOMATIC)
            .color("Black")
            .description("Excellent condition, single owner")
            .city("Warsaw")
            .country("Poland")
            .build();

        testListing = CarListingResponse.builder()
            .id(testListingId)
            .sellerId(testUserId)
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(25000.00))
            .mileage(15000)
            .fuelType(FuelType.PETROL)
            .transmission(Transmission.AUTOMATIC)
            .color("Black")
            .description("Excellent condition, single owner")
            .city("Warsaw")
            .country("Poland")
            .imageUrls(Arrays.asList("https://s3.example.com/img1.jpg", "https://s3.example.com/img2.jpg"))
            .status(ListingStatus.ACTIVE)
            .createdAt(LocalDateTime.now().minusDays(5))
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // ==================== GET /cars (List All) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("GET /cars should return paginated list of active listings")
    void testListAll_Success() throws Exception {
        Page<CarListingResponse> page = new PageImpl<>(
            Collections.singletonList(testListing),
            PageRequest.of(0, 20),
            1
        );
        when(carListingService.getAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/cars")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(testListingId.toString()))
            .andExpect(jsonPath("$.content[0].make").value("BMW"))
            .andExpect(jsonPath("$.content[0].model").value("320i"))
            .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.size").value(20));

        verify(carListingService, times(1)).getAll(any(Pageable.class));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /cars should return empty page when no listings exist")
    void testListAll_Empty() throws Exception {
        Page<CarListingResponse> emptyPage = new PageImpl<>(
            List.of(),
            PageRequest.of(0, 20),
            0
        );
        when(carListingService.getAll(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/cars"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(0));

        verify(carListingService, times(1)).getAll(any(Pageable.class));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /cars should support pagination parameters")
    void testListAll_WithPagination() throws Exception {
        Page<CarListingResponse> page = new PageImpl<>(
            Collections.singletonList(testListing),
            PageRequest.of(1, 10),
            25
        );
        when(carListingService.getAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/cars?page=1&size=10")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(25));

        verify(carListingService, times(1)).getAll(any(Pageable.class));
    }

    // ==================== GET /cars/{id} (Get Single) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("GET /cars/{id} should return single listing by ID")
    void testGetOne_Success() throws Exception {
        when(carListingService.getById(testListingId)).thenReturn(testListing);

        mockMvc.perform(get("/cars/{id}", testListingId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testListingId.toString()))
            .andExpect(jsonPath("$.make").value("BMW"))
            .andExpect(jsonPath("$.model").value("320i"))
            .andExpect(jsonPath("$.year").value(2022))
            .andExpect(jsonPath("$.price").value(25000.00))
            .andExpect(jsonPath("$.mileage").value(15000))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(carListingService, times(1)).getById(testListingId);
    }

    // ==================== GET /cars/my (My Listings) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("GET /cars/my should return user's own listings")
    void testMyListings_Success() throws Exception {
        Page<CarListingResponse> page = new PageImpl<>(
            Collections.singletonList(testListing),
            PageRequest.of(0, 20),
            1
        );
        when(carListingService.getMyListings(eq(testUserId), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/cars/my")
                .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].sellerId").value(testUserId.toString()))
            .andExpect(jsonPath("$.totalElements").value(1));

        verify(carListingService, times(1)).getMyListings(eq(testUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /cars/my should return empty page when user has no listings")
    void testMyListings_Empty() throws Exception {
        Page<CarListingResponse> emptyPage = new PageImpl<>(
            List.of(),
            PageRequest.of(0, 20),
            0
        );
        when(carListingService.getMyListings(eq(testUserId), any(Pageable.class)))
            .thenReturn(emptyPage);

        mockMvc.perform(get("/cars/my")
                .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content.length()").value(0));

        verify(carListingService, times(1)).getMyListings(eq(testUserId), any(Pageable.class));
    }

    // ==================== POST /cars (Create) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("POST /cars should create new listing and return 201")
    void testCreate_Success() throws Exception {
        when(carListingService.create(any(CarListingRequest.class), eq(testUserId))).thenReturn(testListing);

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
            .andExpect(status().isCreated());

        verify(carListingService, times(1)).create(any(CarListingRequest.class), eq(testUserId));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 400 when make is blank")
    void testCreate_ValidationError_BlankMake() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(25000.00))
            .build();

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 400 when model is null")
    void testCreate_ValidationError_NullModel() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("BMW")
            .model(null)
            .year(2022)
            .price(BigDecimal.valueOf(25000.00))
            .build();

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 400 when year is below minimum (1886)")
    void testCreate_ValidationError_InvalidYear() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(1800)
            .price(BigDecimal.valueOf(25000.00))
            .build();

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 400 when year is above maximum (2100)")
    void testCreate_ValidationError_YearTooHigh() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(2150)
            .price(BigDecimal.valueOf(25000.00))
            .build();

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 400 when price is null")
    void testCreate_ValidationError_NullPrice() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(null)
            .build();

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 400 when price is negative")
    void testCreate_ValidationError_InvalidPrice() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(-100))
            .build();

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 400 when mileage is negative")
    void testCreate_ValidationError_NegativeMileage() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(25000.00))
            .mileage(-1000)
            .build();

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).create(any(), any());
    }

    // ==================== PUT /cars/{id} (Update) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("PUT /cars/{id} should update existing listing")
    void testUpdate_Success() throws Exception {
        CarListingRequest updateRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(24000.00))
            .mileage(15000)
            .fuelType(FuelType.PETROL)
            .transmission(Transmission.AUTOMATIC)
            .color("Black")
            .description("Excellent condition, single owner")
            .city("Warsaw")
            .country("Poland")
            .build();

        CarListingResponse updatedListing = CarListingResponse.builder()
            .id(testListingId)
            .sellerId(testUserId)
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(24000.00))
            .mileage(15000)
            .fuelType(FuelType.PETROL)
            .transmission(Transmission.AUTOMATIC)
            .color("Black")
            .description("Excellent condition, single owner")
            .city("Warsaw")
            .country("Poland")
            .imageUrls(List.of("https://s3.example.com/img1.jpg"))
            .status(ListingStatus.ACTIVE)
            .createdAt(LocalDateTime.now().minusDays(5))
            .updatedAt(LocalDateTime.now())
            .build();

        when(carListingService.update(eq(testListingId), any(CarListingRequest.class), eq(testUserId)))
            .thenReturn(updatedListing);

        mockMvc.perform(put("/cars/{id}", testListingId)
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk());

        verify(carListingService, times(1)).update(eq(testListingId), any(CarListingRequest.class), eq(testUserId));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /cars/{id} should validate year constraints")
    void testUpdate_ValidationError_InvalidYear() throws Exception {
        CarListingRequest invalidRequest = CarListingRequest.builder()
            .make("BMW")
            .model("320i")
            .year(1800)
            .price(BigDecimal.valueOf(25000.00))
            .build();

        mockMvc.perform(put("/cars/{id}", testListingId)
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(carListingService, never()).update(any(), any(), any());
    }

    // ==================== PATCH /cars/{id}/sold (Mark as Sold) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("PATCH /cars/{id}/sold should mark listing as sold")
    void testMarkAsSold_Success() throws Exception {
        CarListingResponse soldListing = CarListingResponse.builder()
            .id(testListingId)
            .sellerId(testUserId)
            .make("BMW")
            .model("320i")
            .year(2022)
            .price(BigDecimal.valueOf(25000.00))
            .mileage(15000)
            .fuelType(FuelType.PETROL)
            .transmission(Transmission.AUTOMATIC)
            .color("Black")
            .description("Excellent condition, single owner")
            .city("Warsaw")
            .country("Poland")
            .imageUrls(List.of("https://s3.example.com/img1.jpg"))
            .status(ListingStatus.SOLD)
            .createdAt(LocalDateTime.now().minusDays(5))
            .updatedAt(LocalDateTime.now())
            .build();

        when(carListingService.markAsSold(testListingId, testUserId))
            .thenReturn(soldListing);

        mockMvc.perform(patch("/cars/{id}/sold", testListingId)
                .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SOLD"))
            .andExpect(jsonPath("$.id").value(testListingId.toString()));

        verify(carListingService, times(1)).markAsSold(testListingId, testUserId);
    }

    // ==================== DELETE /cars/{id} (Soft Delete) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("DELETE /cars/{id} should soft delete listing")
    void testDelete_Success() throws Exception {
        doNothing().when(carListingService).delete(testListingId, testUserId);

        mockMvc.perform(delete("/cars/{id}", testListingId)
                .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isNoContent());

        verify(carListingService, times(1)).delete(testListingId, testUserId);
    }

    // ==================== POST /cars/{id}/images (Upload Images) TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("POST /cars/{id}/images should upload images and return URLs")
    void testUploadImages_Success() throws Exception {
        List<String> uploadedUrls = Arrays.asList(
            "https://s3.example.com/upload1.jpg",
            "https://s3.example.com/upload2.jpg"
        );
        when(carListingService.uploadImages(eq(testListingId), eq(testUserId), anyList()))
            .thenReturn(uploadedUrls);

        MockMultipartFile file1 = new MockMultipartFile(
            "files", "test1.jpg", MediaType.IMAGE_JPEG_VALUE, "test1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
            "files", "test2.jpg", MediaType.IMAGE_JPEG_VALUE, "test2".getBytes());

        mockMvc.perform(multipart("/cars/{id}/images", testListingId)
                .file(file1)
                .file(file2)
                .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("https://s3.example.com/upload1.jpg"))
            .andExpect(jsonPath("$[1]").value("https://s3.example.com/upload2.jpg"))
            .andExpect(jsonPath("$.length()").value(2));

        verify(carListingService, times(1)).uploadImages(eq(testListingId), eq(testUserId), anyList());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars/{id}/images with single file should upload successfully")
    void testUploadImages_SingleFile() throws Exception {
        List<String> uploadedUrls = List.of("https://s3.example.com/upload1.jpg");
        when(carListingService.uploadImages(eq(testListingId), eq(testUserId), anyList()))
            .thenReturn(uploadedUrls);

        MockMultipartFile file = new MockMultipartFile(
            "files", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test".getBytes());

        mockMvc.perform(multipart("/cars/{id}/images", testListingId)
                .file(file)
                .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        verify(carListingService, times(1)).uploadImages(eq(testListingId), eq(testUserId), anyList());
    }

    // ==================== RESPONSE STRUCTURE & HEADERS ====================

    @Test
    @WithMockUser
    @DisplayName("Response should include all required fields for car listing")
    void testCarListingResponseStructure() throws Exception {
        when(carListingService.getById(testListingId)).thenReturn(testListing);

        mockMvc.perform(get("/cars/{id}", testListingId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.sellerId").exists())
            .andExpect(jsonPath("$.make").exists())
            .andExpect(jsonPath("$.model").exists())
            .andExpect(jsonPath("$.year").exists())
            .andExpect(jsonPath("$.price").exists())
            .andExpect(jsonPath("$.mileage").exists())
            .andExpect(jsonPath("$.fuelType").exists())
            .andExpect(jsonPath("$.transmission").exists())
            .andExpect(jsonPath("$.color").exists())
            .andExpect(jsonPath("$.description").exists())
            .andExpect(jsonPath("$.city").exists())
            .andExpect(jsonPath("$.country").exists())
            .andExpect(jsonPath("$.imageUrls").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());

        verify(carListingService, times(1)).getById(testListingId);
    }

    @Test
    @WithMockUser
    @DisplayName("Controller should extract UUID from X-User-Id header")
    void testUserIdHeaderExtraction() throws Exception {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        UUID parsedId = UUID.fromString(userId);
        Page<CarListingResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(carListingService.getMyListings(eq(parsedId), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/cars/my")
                .header("X-User-Id", userId))
            .andExpect(status().isOk());

        verify(carListingService, times(1)).getMyListings(eq(parsedId), any(Pageable.class));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /cars should return 201 Created status code")
    void testCreate_ResponseStatus() throws Exception {
        when(carListingService.create(any(CarListingRequest.class), any(UUID.class)))
            .thenReturn(testListing);

        mockMvc.perform(post("/cars")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
            .andExpect(status().isCreated());

        verify(carListingService, times(1)).create(any(CarListingRequest.class), any(UUID.class));
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /cars/{id} should return 204 No Content status code")
    void testDelete_ResponseStatus() throws Exception {
        doNothing().when(carListingService).delete(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/cars/{id}", testListingId)
                .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isNoContent());

        verify(carListingService, times(1)).delete(any(UUID.class), any(UUID.class));
    }
}