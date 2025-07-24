package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import swp.project.adn_backend.dto.response.result.ResultDetailResponse;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.PatientStatus;
import swp.project.adn_backend.enums.ResultStatus;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.ResultDetailsMapper;
import swp.project.adn_backend.repository.*;
import swp.project.adn_backend.service.result.ResultDetailsService;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultDetailsServiceTest {

    @Mock
    private ResultDetailRepository resultDetailRepository;
    @Mock
    private ResultDetailsMapper resultDetailsMapper;
    @Mock
    private SampleRepository sampleRepository;
    @Mock
    private ResultRepository resultRepository;
    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private ResultDetailsService resultDetailsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateResultDetail_successfulCreation() {
        long appointmentId = 1L;
        Appointment appointment = mock(Appointment.class);
        ResultLocus locus = mock(ResultLocus.class);
        when(locus.getPi()).thenReturn(2.0);
        List<ResultLocus> loci = Collections.singletonList(locus);

        Patient patient = mock(Patient.class);
        Sample sample = mock(Sample.class);
        when(sample.getCollectionDate()).thenReturn(LocalDate.of(2023, 1, 1));
        List<Sample> samples = Collections.singletonList(sample);
        when(patient.getSamples()).thenReturn(samples);
        List<Patient> patients = Collections.singletonList(patient);

        when(appointment.getResultLoci()).thenReturn(loci);
        when(appointment.getPatients()).thenReturn(patients);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        ResultDetailResponse response = new ResultDetailResponse();
        when(resultDetailsMapper.toResultDetailResponse(any(ResultDetail.class))).thenReturn(response);

        ResultDetailResponse actual = resultDetailsService.CreateResultDetail(appointmentId);

        assertSame(response, actual);
        verify(resultRepository).save(any(Result.class));
        verify(resultDetailRepository).save(any(ResultDetail.class));
        verify(resultDetailsMapper).toResultDetailResponse(any(ResultDetail.class));
    }

    @Test
    void testCreateResultDetail_combinedPiAndProbabilityCalculation() {
        long appointmentId = 2L;
        Appointment appointment = mock(Appointment.class);

        ResultLocus locus1 = mock(ResultLocus.class);
        ResultLocus locus2 = mock(ResultLocus.class);
        when(locus1.getPi()).thenReturn(2.0);
        when(locus2.getPi()).thenReturn(3.0);
        List<ResultLocus> loci = Arrays.asList(locus1, locus2);

        Patient patient = mock(Patient.class);
        Sample sample = mock(Sample.class);
        when(sample.getCollectionDate()).thenReturn(LocalDate.of(2023, 2, 2));
        when(patient.getSamples()).thenReturn(Collections.singletonList(sample));
        when(appointment.getPatients()).thenReturn(Collections.singletonList(patient));
        when(appointment.getResultLoci()).thenReturn(loci);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        ArgumentCaptor<ResultDetail> resultDetailCaptor = ArgumentCaptor.forClass(ResultDetail.class);
        when(resultDetailsMapper.toResultDetailResponse(any(ResultDetail.class))).thenReturn(new ResultDetailResponse());

        resultDetailsService.CreateResultDetail(appointmentId);

        verify(resultDetailRepository).save(resultDetailCaptor.capture());
        ResultDetail savedDetail = resultDetailCaptor.getValue();

        double expectedCombinedPi = 2.0 * 3.0;
        double expectedProbability = (expectedCombinedPi / (expectedCombinedPi + 1)) * 100;

        assertEquals(expectedCombinedPi, savedDetail.getCombinedPaternityIndex(), 0.0001);
        assertEquals(expectedProbability, savedDetail.getPaternityProbability(), 0.0001);
        assertTrue(savedDetail.getResultSummary().contains(String.format("Combined PI: %.2f", expectedCombinedPi)));
        assertTrue(savedDetail.getResultSummary().contains(String.format("Probability: %.4f%%", expectedProbability)));
    }

    @Test
    void testCreateResultDetail_patientStatusUpdate() {
        long appointmentId = 3L;
        Appointment appointment = mock(Appointment.class);

        ResultLocus locus = mock(ResultLocus.class);
        when(locus.getPi()).thenReturn(2.0);
        List<ResultLocus> loci = Collections.singletonList(locus);

        Patient patient1 = mock(Patient.class);
        Patient patient2 = mock(Patient.class);
        Sample sample = mock(Sample.class);
        when(sample.getCollectionDate()).thenReturn(LocalDate.of(2023, 3, 3));
        when(patient1.getSamples()).thenReturn(Collections.singletonList(sample));
        when(patient2.getSamples()).thenReturn(Collections.singletonList(sample));
        List<Patient> patients = Arrays.asList(patient1, patient2);

        when(appointment.getResultLoci()).thenReturn(loci);
        when(appointment.getPatients()).thenReturn(patients);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(resultDetailsMapper.toResultDetailResponse(any(ResultDetail.class))).thenReturn(new ResultDetailResponse());

        resultDetailsService.CreateResultDetail(appointmentId);

        verify(patient1).setPatientStatus(PatientStatus.COMPLETED);
        verify(patient2).setPatientStatus(PatientStatus.COMPLETED);
    }

    @Test
    void testCreateResultDetail_appointmentNotFound() {
        long appointmentId = 4L;
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> resultDetailsService.CreateResultDetail(appointmentId));
        assertEquals(ErrorCodeUser.SAMPLE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testCreateResultDetail_noResultLocus() {
        long appointmentId = 5L;
        Appointment appointment = mock(Appointment.class);
        when(appointment.getResultLoci()).thenReturn(Collections.emptyList());
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        AppException ex = assertThrows(AppException.class, () -> resultDetailsService.CreateResultDetail(appointmentId));
        assertEquals(ErrorCodeUser.NO_RESULT_LOCUS_FOUND_FOR_SAMPLE, ex.getErrorCode());
    }

    @Test
    void testCreateResultDetail_missingPatientsOrSamples() {
        long appointmentId = 6L;
        Appointment appointment = mock(Appointment.class);

        // Case 1: patients list is null
        when(appointment.getResultLoci()).thenReturn(Collections.singletonList(mock(ResultLocus.class)));
        when(appointment.getPatients()).thenReturn(null);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        assertThrows(NullPointerException.class, () -> resultDetailsService.CreateResultDetail(appointmentId));

        // Case 2: patients list is empty
        when(appointment.getPatients()).thenReturn(Collections.emptyList());
        assertThrows(NoSuchElementException.class, () -> resultDetailsService.CreateResultDetail(appointmentId));

        // Case 3: samples list is null for patient
        Patient patient = mock(Patient.class);
        when(patient.getSamples()).thenReturn(null);
        when(appointment.getPatients()).thenReturn(Collections.singletonList(patient));
        assertThrows(NullPointerException.class, () -> resultDetailsService.CreateResultDetail(appointmentId));

        // Case 4: samples list is empty for patient
        when(patient.getSamples()).thenReturn(Collections.emptyList());
        assertThrows(NoSuchElementException.class, () -> resultDetailsService.CreateResultDetail(appointmentId));
    }

}