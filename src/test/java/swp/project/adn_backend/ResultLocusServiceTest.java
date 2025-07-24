package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import swp.project.adn_backend.dto.request.result.ResultLocusRequest;
import swp.project.adn_backend.dto.response.result.ResultDetailResponse;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.*;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.ResultDetailsMapper;
import swp.project.adn_backend.mapper.ResultLocusMapper;
import swp.project.adn_backend.repository.*;
import swp.project.adn_backend.service.result.ResultLocusService;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultLocusServiceTest {

    @Mock
    ResultRepository resultRepository;
    @Mock
    ResultLocusMapper resultLocusMapper;
    @Mock
    SampleRepository sampleRepository;
    @Mock
    ResultLocusRepository resultLocusRepository;
    @Mock
    ResultAlleleRepository resultAlleleRepository;
    @Mock
    AppointmentRepository appointmentRepository;
    @Mock
    LocusRepository locusRepository;
    @Mock
    ResultDetailRepository resultDetailRepository;
    @Mock
    ResultDetailsMapper resultDetailsMapper;

    @InjectMocks
    ResultLocusService resultLocusService;

    Sample sample1;
    Sample sample2;
    Appointment appointment;
    Locus locus;
    ResultAllele fatherAllele1, fatherAllele2, childAllele1, childAllele2;
    ResultLocusRequest resultLocusRequest;

    @BeforeEach
    void setUp() {
        sample1 = new Sample();
        sample1.setSampleId(1L);
        sample1.setSampleStatus(SampleStatus.COMPLETED);
        sample1.setSampleCode("S1");
        sample1.setCollectionDate(LocalDate.of(2024, 6, 1));

        sample2 = new Sample();
        sample2.setSampleId(2L);
        sample2.setSampleStatus(SampleStatus.COMPLETED);
        sample2.setSampleCode("S2");
        sample2.setCollectionDate(LocalDate.of(2024, 6, 2));

        appointment = new Appointment();
        appointment.setAppointmentId(10L);
        appointment.setAppointmentStatus(AppointmentStatus.PENDING);
        appointment.setPatients(new ArrayList<>());

        locus = new Locus();
        locus.setLocusId(100L);
        locus.setLocusName("D8S1179");

        fatherAllele1 = new ResultAllele();
        fatherAllele1.setAlleleStatus(AlleleStatus.VALID);
        fatherAllele1.setAlleleValue(11.0);
        fatherAllele1.setLocus(locus);

        fatherAllele2 = new ResultAllele();
        fatherAllele2.setAlleleStatus(AlleleStatus.VALID);
        fatherAllele2.setAlleleValue(12.0);
        fatherAllele2.setLocus(locus);

        childAllele1 = new ResultAllele();
        childAllele1.setAlleleStatus(AlleleStatus.VALID);
        childAllele1.setAlleleValue(11.0);
        childAllele1.setLocus(locus);

        childAllele2 = new ResultAllele();
        childAllele2.setAlleleStatus(AlleleStatus.VALID);
        childAllele2.setAlleleValue(12.0);
        childAllele2.setLocus(locus);

        sample1.setResultAlleles(Arrays.asList(fatherAllele1, fatherAllele2));
        sample2.setResultAlleles(Arrays.asList(childAllele1, childAllele2));

        resultLocusRequest = new ResultLocusRequest();
    }

    @Test
    void testCreateResultLocusAndDetail_Success() {
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample1));
        when(sampleRepository.findById(2L)).thenReturn(Optional.of(sample2));
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(locusRepository.findById(100L)).thenReturn(Optional.of(locus));
        when(resultDetailsMapper.toResultDetailResponse(any())).thenReturn(new ResultDetailResponse());

        ResultDetailResponse response = resultLocusService.createResultLocusAndDetail(1L, 2L, 10L, resultLocusRequest);

        assertNotNull(response);
        verify(resultRepository).save(any(Result.class));
        verify(resultDetailRepository).save(any(ResultDetail.class));
        verify(resultLocusRepository, atLeastOnce()).save(any(ResultLocus.class));
        verify(resultDetailsMapper).toResultDetailResponse(any(ResultDetail.class));
    }

    @Test
    void testCalculatePIAndPaternityProbability_Correctness() {
        // Heterozygous child: father (11,12), child (11,12)
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample1));
        when(sampleRepository.findById(2L)).thenReturn(Optional.of(sample2));
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(locusRepository.findById(100L)).thenReturn(Optional.of(locus));
        when(resultDetailsMapper.toResultDetailResponse(any())).thenAnswer(invocation -> {
            ResultDetail detail = invocation.getArgument(0);
            assertTrue(detail.getCombinedPaternityIndex() > 0);
            assertTrue(detail.getPaternityProbability() > 0);
            return new ResultDetailResponse();
        });

        ResultDetailResponse response = resultLocusService.createResultLocusAndDetail(1L, 2L, 10L, resultLocusRequest);

        assertNotNull(response);
    }

    @Test
    void testAppointmentAndRelatedEntitiesUpdate_OnResultCreation() {
        // CIVIL service with AT_CLINIC method, slot and staff
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceType(ServiceType.CIVIL);
        CivilService civilService = new CivilService();
        civilService.setSampleCollectionMethods(Set.of(SampleCollectionMethod.AT_CLINIC));
        serviceTest.setCivilServices(List.of(civilService));
        appointment.setServices(serviceTest);

        Slot slot = new Slot();
        slot.setSlotStatus(SlotStatus.BOOKED);
        Staff staff = new Staff();
        staff.setRole("SAMPLE_COLLECTOR");
        slot.setStaff(List.of(staff));
        appointment.setSlot(slot);

        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample1));
        when(sampleRepository.findById(2L)).thenReturn(Optional.of(sample2));
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(locusRepository.findById(100L)).thenReturn(Optional.of(locus));
        when(resultDetailsMapper.toResultDetailResponse(any())).thenReturn(new ResultDetailResponse());

        resultLocusService.createResultLocusAndDetail(1L, 2L, 10L, resultLocusRequest);

        assertEquals(SlotStatus.COMPLETED, slot.getSlotStatus());
        assertEquals(staff, appointment.getStaff());
        assertEquals(AppointmentStatus.WAITING_MANAGER_APPROVAL, appointment.getAppointmentStatus());
        assertEquals("kết quả xét nghiệm đang được xác nhận", appointment.getNote());
    }

    @Test
    void testCreateResultLocusAndDetail_SampleNotExists() {
        when(sampleRepository.findById(1L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                resultLocusService.createResultLocusAndDetail(1L, 2L, 10L, resultLocusRequest)
        );
        assertEquals(ErrorCodeUser.SAMPLE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testCreateResultLocusAndDetail_SampleRejected() {
        sample1.setSampleStatus(SampleStatus.REJECTED);
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample1));
        when(sampleRepository.findById(2L)).thenReturn(Optional.of(sample2));
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                resultLocusService.createResultLocusAndDetail(1L, 2L, 10L, resultLocusRequest)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("không hợp lệ"),
                "Expected message to contain 'không hợp lệ' but was: " + ex.getMessage());
    }

    @Test
    void testCreateResultLocusAndDetail_NoAlleleMatch() {
        // Parent alleles (11,12), child alleles (13,14) - no match
        fatherAllele1.setAlleleValue(11.0);
        fatherAllele2.setAlleleValue(12.0);
        childAllele1.setAlleleValue(13.0);
        childAllele2.setAlleleValue(14.0);
        sample1.setResultAlleles(Arrays.asList(fatherAllele1, fatherAllele2));
        sample2.setResultAlleles(Arrays.asList(childAllele1, childAllele2));

        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample1));
        when(sampleRepository.findById(2L)).thenReturn(Optional.of(sample2));
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(locusRepository.findById(100L)).thenReturn(Optional.of(locus));
        when(resultDetailsMapper.toResultDetailResponse(any())).thenAnswer(invocation -> {
            ResultDetail detail = invocation.getArgument(0);
            assertEquals(0.0, detail.getCombinedPaternityIndex());
            assertEquals(0.0, detail.getPaternityProbability());
            return new ResultDetailResponse();
        });

        ResultDetailResponse response = resultLocusService.createResultLocusAndDetail(1L, 2L, 10L, resultLocusRequest);

        assertNotNull(response);
    }
}