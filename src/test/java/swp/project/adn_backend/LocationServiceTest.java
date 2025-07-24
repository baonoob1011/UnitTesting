package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import swp.project.adn_backend.dto.request.Location.LocationRequest;
import swp.project.adn_backend.dto.request.Location.LocationResponse;
import swp.project.adn_backend.entity.Location;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.LocationMapper;
import swp.project.adn_backend.repository.LocationRepository;
import swp.project.adn_backend.service.Location.LocationService;

import java.util.List;
import java.util.Optional;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

public class LocationServiceTest {
    @InjectMocks
    private LocationService locationService;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationMapper locationMapper;

    private Location location;
    private LocationRequest locationRequest;
    private LocationResponse locationResponse;

    @BeforeEach
    void setUp() {
        locationRequest = new LocationRequest();
        // set fields here if needed

        location = new Location();
        location.setLocationId(1L);

        locationResponse = new LocationResponse();
        // set fields here if needed
    }
    @Test
    void testCreateLocation_Success() {
        when(locationMapper.toLocation(locationRequest)).thenReturn(location);
        when(locationRepository.save(location)).thenReturn(location);

        Location result = locationService.createLocation(locationRequest);

        assertNotNull(result);
        assertEquals(location, result);
        verify(locationMapper).toLocation(locationRequest);
        verify(locationRepository).save(location);
    }
    @Test
    void testGetAllLocation_Success() {
        List<Location> locations = List.of(location);
        List<LocationResponse> responses = List.of(locationResponse);

        when(locationRepository.findAll()).thenReturn(locations);
        when(locationMapper.toLocationResponse(locations)).thenReturn(responses);

        List<LocationResponse> result = locationService.getAllLocation();

        assertEquals(1, result.size());
        assertEquals(locationResponse, result.get(0));
        verify(locationRepository).findAll();
        verify(locationMapper).toLocationResponse(locations);
    }
    @Test
    void testDeleteLocation_NotFound() {
        when(locationRepository.findById(1L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            locationService.deleteLocation(1L);
        });

        assertEquals(ErrorCodeUser.LOCATION_NOT_EXISTS, exception.getErrorCode());
    }
    @Test
    void testDeleteLocation_Success() {
        Location location = new Location();
        location.setLocationId(1L);

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));

        locationService.deleteLocation(1L);

        verify(locationRepository).delete(location);
    }


}
