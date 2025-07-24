package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import swp.project.adn_backend.dto.request.result.ResultRequest;
import swp.project.adn_backend.dto.response.result.ResultResponse;
import swp.project.adn_backend.entity.Result;
import swp.project.adn_backend.entity.Sample;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.ResultStatus;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.ResultMapper;
import swp.project.adn_backend.repository.ResultRepository;
import swp.project.adn_backend.repository.SampleRepository;
import swp.project.adn_backend.service.result.ResultService;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultServiceTest {

    @Mock
    private ResultMapper resultMapper;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private SampleRepository sampleRepository;

    @InjectMocks
    private ResultService resultService;

    private ResultRequest resultRequest;
    private Sample sample;
    private Result result;
    private ResultResponse resultResponse;

    @BeforeEach
    void setUp() {
        resultRequest = new ResultRequest(
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 2),
                ResultStatus.IN_PROGRESS
        );
        sample = new Sample();
        sample.setSampleId(1L);
        sample.setCollectionDate(LocalDate.of(2024, 6, 1));
        result = new Result();
        result.setCollectionDate(LocalDate.of(2024, 6, 1));
        result.setResultStatus(ResultStatus.IN_PROGRESS);
        resultResponse = new ResultResponse(
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 2),
                ResultStatus.IN_PROGRESS
        );
    }

    @Test
    void testCreateResult_SuccessfulCreation() {
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(resultMapper.toResult(resultRequest)).thenReturn(result);
        when(resultMapper.toResultResponse(result)).thenReturn(resultResponse);

        ResultResponse response = resultService.createResult(resultRequest, 1L);

        assertNotNull(response);
        assertEquals(resultResponse.getCollectionDate(), response.getCollectionDate());
        assertEquals(resultResponse.getResultDate(), response.getResultDate());
        assertEquals(resultResponse.getResultStatus(), response.getResultStatus());
        verify(sampleRepository).findById(1L);
        verify(resultMapper).toResult(resultRequest);
        verify(resultMapper).toResultResponse(result);
    }

    @Test
    void testCreateResult_StatusSetToInProgress() {
        resultRequest.setResultStatus(ResultStatus.IN_PROGRESS); // Even if set, should be IN_PROGRESS
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(resultMapper.toResult(resultRequest)).thenReturn(result);
        when(resultMapper.toResultResponse(result)).thenReturn(resultResponse);

        ResultResponse response = resultService.createResult(resultRequest, 1L);

        assertEquals(ResultStatus.IN_PROGRESS, result.getResultStatus());
        assertEquals(ResultStatus.IN_PROGRESS, response.getResultStatus());
    }

    @Test
    void testCreateResult_CollectionDateMatchesSample() {
        LocalDate sampleCollectionDate = LocalDate.of(2024, 5, 20);
        sample.setCollectionDate(sampleCollectionDate);
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(resultMapper.toResult(resultRequest)).thenReturn(result);
        when(resultMapper.toResultResponse(result)).thenReturn(resultResponse);

        resultService.createResult(resultRequest, 1L);

        assertEquals(sampleCollectionDate, result.getCollectionDate());
    }

    @Test
    void testCreateResult_SampleNotExistsException() {
        when(sampleRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                resultService.createResult(resultRequest, 99L)
        );
        assertEquals(ErrorCodeUser.SAMPLE_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void testCreateResult_NullFieldsInRequest() {
        ResultRequest nullRequest = new ResultRequest(null, null, null);
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(resultMapper.toResult(nullRequest)).thenReturn(new Result());
        when(resultMapper.toResultResponse(any(Result.class))).thenReturn(new ResultResponse(null, null, ResultStatus.IN_PROGRESS));

        ResultResponse response = resultService.createResult(nullRequest, 1L);

        assertNotNull(response);
        assertNull(response.getCollectionDate());
        assertNull(response.getResultDate());
        assertEquals(ResultStatus.IN_PROGRESS, response.getResultStatus());
    }

    @Test
    void testCreateResult_MappingFailureException() {
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(resultMapper.toResult(resultRequest)).thenThrow(new RuntimeException("Mapping failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                resultService.createResult(resultRequest, 1L)
        );
        assertEquals("Mapping failed", exception.getMessage());
    }
}