package com.carmarket.car.service;

import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CarListingService Test")
@ExtendWith(MockitoExtension.class)
class CarListingServiceTest {

    @Mock
    private CarListingRepository repository;
    @Mock
    private CarListingMapper mapper;
    @Mock
    private CarEventProducer eventProducer;
    @Mock
    private S3Service s3Service;

    @InjectMocks
    private CarListingService carListingService;
}