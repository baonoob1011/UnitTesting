package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.dao.DataIntegrityViolationException;
import swp.project.adn_backend.dto.InfoDTO.LocusInfoDTO;
import swp.project.adn_backend.dto.request.result.LocusRequest;
import swp.project.adn_backend.dto.response.result.LocusResponse;
import swp.project.adn_backend.entity.Locus;
import swp.project.adn_backend.mapper.LocusMapper;
import swp.project.adn_backend.repository.LocusRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import swp.project.adn_backend.service.result.LocusService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocusServiceTest {

    @Mock
    private LocusRepository locusRepository;

    @Mock
    private LocusMapper locusMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<LocusInfoDTO> typedQuery;

    @InjectMocks
    private LocusService locusService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        locusService = new LocusService(locusRepository, locusMapper, entityManager);
    }

    @Test
    void testCreateLocusWithValidRequest() {
        LocusRequest locusRequest = new LocusRequest();
        locusRequest.setLocusName("D1S80");
        locusRequest.setDescription("Description");

        Locus locus = new Locus();
        locus.setLocusName("D1S80");
        locus.setDescription("Description");

        LocusResponse locusResponse = new LocusResponse();
        locusResponse.setLocusId(1L);
        locusResponse.setLocusName("D1S80");
        locusResponse.setDescription("Description");

        when(locusMapper.toLocus(locusRequest)).thenReturn(locus);
        when(locusRepository.save(locus)).thenReturn(locus);
        when(locusMapper.toLocusResponse(locus)).thenReturn(locusResponse);

        LocusResponse result = locusService.createLocus(locusRequest);

        assertNotNull(result);
        assertEquals("D1S80", result.getLocusName());
        assertEquals("Description", result.getDescription());
        verify(locusRepository).save(locus);
        verify(locusMapper).toLocus(locusRequest);
        verify(locusMapper).toLocusResponse(locus);
    }

    @Test
    void testGetAllLocusReturnsAllRecords() {
        LocusInfoDTO dto1 = new LocusInfoDTO(1L, "D1S80", "Desc1");
        LocusInfoDTO dto2 = new LocusInfoDTO(2L, "TH01", "Desc2");
        List<LocusInfoDTO> expectedList = Arrays.asList(dto1, dto2);

        when(entityManager.createQuery(anyString(), eq(LocusInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(expectedList);

        List<LocusInfoDTO> result = locusService.getAllLocus();

        assertEquals(2, result.size());
        assertEquals("D1S80", result.get(0).getLocusName());
        assertEquals("TH01", result.get(1).getLocusName());
        verify(entityManager).createQuery(anyString(), eq(LocusInfoDTO.class));
        verify(typedQuery).getResultList();
    }

    @Test
    void testLocusRequestToEntityAndEntityToResponseMapping() {
        LocusRequest locusRequest = new LocusRequest();
        locusRequest.setLocusName("D2S1338");
        locusRequest.setDescription("Test Desc");

        Locus locus = new Locus();
        locus.setLocusName("D2S1338");
        locus.setDescription("Test Desc");

        LocusResponse locusResponse = new LocusResponse();
        locusResponse.setLocusId(5L);
        locusResponse.setLocusName("D2S1338");
        locusResponse.setDescription("Test Desc");

        when(locusMapper.toLocus(locusRequest)).thenReturn(locus);
        when(locusRepository.save(locus)).thenReturn(locus);
        when(locusMapper.toLocusResponse(locus)).thenReturn(locusResponse);

        LocusResponse result = locusService.createLocus(locusRequest);

        assertEquals(locusRequest.getLocusName(), locus.getLocusName());
        assertEquals(locusRequest.getDescription(), locus.getDescription());
        assertEquals(locus.getLocusName(), result.getLocusName());
        assertEquals(locus.getDescription(), result.getDescription());
    }

    @Test
    void testCreateLocusWithDuplicateLocusName() {
        LocusRequest locusRequest = new LocusRequest();
        locusRequest.setLocusName("D1S80");
        locusRequest.setDescription("Duplicate");

        Locus locus = new Locus();
        locus.setLocusName("D1S80");
        locus.setDescription("Duplicate");

        when(locusMapper.toLocus(locusRequest)).thenReturn(locus);
        when(locusRepository.save(locus)).thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThrows(DataIntegrityViolationException.class, () -> locusService.createLocus(locusRequest));
        verify(locusRepository).save(locus);
    }

    @Test
    void testCreateLocusWithNullOrIncompleteRequest() {
        // Null request: Expect no exception if your service handles it gracefully
        assertDoesNotThrow(() -> locusService.createLocus(null));

        // Incomplete request (missing locusName)
        LocusRequest incompleteRequest = new LocusRequest();
        incompleteRequest.setDescription("No name");

        when(locusMapper.toLocus(incompleteRequest)).thenReturn(new Locus());

        when(locusRepository.save(any(Locus.class)))
                .thenThrow(new DataIntegrityViolationException("locusName required"));

        assertThrows(DataIntegrityViolationException.class, () -> locusService.createLocus(incompleteRequest));
    }


    @Test
    void testGetAllLocusWhenNoRecordsExist() {
        when(entityManager.createQuery(anyString(), eq(LocusInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(Collections.emptyList());

        List<LocusInfoDTO> result = locusService.getAllLocus();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(entityManager).createQuery(anyString(), eq(LocusInfoDTO.class));
        verify(typedQuery).getResultList();
    }
}