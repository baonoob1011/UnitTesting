package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import swp.project.adn_backend.dto.request.result.AllelePairRequest;
import swp.project.adn_backend.dto.response.result.LocusResponse;
import swp.project.adn_backend.dto.response.result.PatientAlleleResponse;
import swp.project.adn_backend.dto.response.result.ResultAlleleResponse;
import swp.project.adn_backend.dto.response.result.SampleAlleleResponse;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.AlleleStatus;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.PatientStatus;
import swp.project.adn_backend.enums.SampleStatus;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.AppointmentMapper;
import swp.project.adn_backend.mapper.ResultAlleleMapper;
import swp.project.adn_backend.repository.*;
import jakarta.persistence.EntityManager;
import swp.project.adn_backend.service.registerServiceTestService.AllAlleleResponse;
import swp.project.adn_backend.service.result.ResultAlleleService;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultAlleleServiceTest {

    @Mock
    private ResultAlleleRepository resultAlleleRepository;
    @Mock
    private ResultAlleleMapper resultAlleleMapper;
    @Mock
    private SampleRepository sampleRepository;
    @Mock
    private LocusRepository locusRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private AppointmentMapper appointmentMapper;

    @InjectMocks
    private ResultAlleleService resultAlleleService;

    @BeforeEach
    void setUp() {
        // MockitoAnnotations.openMocks(this); // Not needed with @ExtendWith(MockitoExtension.class)
    }

    @Test
    void testCreateAllelePair_LocusNotFound() {
        long sampleId = 1L;
        long locusId = 2L;
        AllelePairRequest request = new AllelePairRequest();
        request.setAllele1(10.5);
        request.setAllele2(11.5);
        request.setAlleleStatus(AlleleStatus.INVALID);

        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.COLLECTED);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(locusRepository.findById(locusId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                resultAlleleService.createAllelePair(request, sampleId, locusId)
        );

        assertEquals(ErrorCodeUser.LOCUS_NOT_EXISTS, ex.getErrorCode());
    }




    @Test
    void testGetAllAlleleOfSample_Success() {
        long patientId = 1L;
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        patient.setFullName("John Doe");
        patient.setPatientStatus(PatientStatus.COMPLETED);

        Sample sample = new Sample();
        sample.setSampleId(10L);
        sample.setSampleCode("S001");
        sample.setSampleStatus(SampleStatus.COLLECTED);

        ResultAllele allele = new ResultAllele();
        allele.setAlleleId(100L);
        allele.setAlleleValue(12.3);
        allele.setAllelePosition("1");
        allele.setAlleleStatus(AlleleStatus.INVALID);

        Locus locus = new Locus();
        locus.setLocusId(5L);
        locus.setLocusName("D5S818");
        allele.setLocus(locus);

        sample.setResultAlleles(List.of(allele));
        patient.setSamples(List.of(sample));

        PatientAlleleResponse patientAlleleResponse = new PatientAlleleResponse();
        patientAlleleResponse.setPatientId(patientId);

        SampleAlleleResponse sampleAlleleResponse = new SampleAlleleResponse();
        sampleAlleleResponse.setSampleId(sample.getSampleId());

        ResultAlleleResponse resultAlleleResponse = new ResultAlleleResponse();
        resultAlleleResponse.setAlleleId(allele.getAlleleId());
        resultAlleleResponse.setAlleleValue(allele.getAlleleValue());
        resultAlleleResponse.setAllelePosition(allele.getAllelePosition());
        resultAlleleResponse.setAlleleStatus(allele.getAlleleStatus());

        LocusResponse locusResponse = new LocusResponse();
        locusResponse.setLocusId(locus.getLocusId());
        locusResponse.setLocusName(locus.getLocusName());

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(appointmentMapper.toPatientAppointmentResponse(patient)).thenReturn(patientAlleleResponse);
        when(appointmentMapper.toSampleAlleleResponses(patient.getSamples())).thenReturn(List.of(sampleAlleleResponse));
        when(resultAlleleMapper.toResultAlleleResponse(allele)).thenReturn(resultAlleleResponse);
        when(appointmentMapper.toLocusResponses(locus)).thenReturn(locusResponse);

        swp.project.adn_backend.service.registerServiceTestService.AllAlleleResponse response = resultAlleleService.getAllAlleleOfSample(patientId);

        assertNotNull(response);
        assertEquals(patientId, response.getPatientAppointmentResponse().getPatientId());
        assertEquals(1, response.getSampleAlleleResponse().size());
        assertEquals(sample.getSampleId(), response.getSampleAlleleResponse().get(0).getSampleId());
        assertEquals(1, response.getResultAlleleResponse().size());
        assertEquals(allele.getAlleleId(), response.getResultAlleleResponse().get(0).getAlleleId());
        assertEquals(locus.getLocusId(), response.getResultAlleleResponse().get(0).getLocusResponse().getLocusId());
    }

    @Test
    void testCreateAllelePair_RejectSampleOnInvalidAlleles() {
        long sampleId = 1L;
        long locusId = 2L;

        AllelePairRequest request = new AllelePairRequest();
        request.setAllele1(10.5);
        request.setAllele2(11.5);
        request.setAlleleStatus(AlleleStatus.INVALID);

        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.COLLECTED);

        Locus locus = new Locus();
        locus.setLocusId(locusId);
        locus.setLocusName("D3S1358");

        ResultAllele allele1 = new ResultAllele();
        allele1.setAlleleValue(request.getAllele1());
        allele1.setAllelePosition("1");
        allele1.setAlleleStatus(request.getAlleleStatus());
        allele1.setSample(sample);
        allele1.setLocus(locus);

        ResultAllele allele2 = new ResultAllele();
        allele2.setAlleleValue(request.getAllele2());
        allele2.setAllelePosition("2");
        allele2.setAlleleStatus(request.getAlleleStatus());
        allele2.setSample(sample);
        allele2.setLocus(locus);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(locusRepository.findById(locusId)).thenReturn(Optional.of(locus));
        when(resultAlleleRepository.save(any(ResultAllele.class)))
                .thenReturn(allele1)
                .thenReturn(allele2);

        // Fix mock mapper to avoid NullPointerException
        when(resultAlleleMapper.toResultAlleleResponse(any(ResultAllele.class)))
                .thenAnswer(invocation -> {
                    ResultAllele allele = invocation.getArgument(0);
                    ResultAlleleResponse response = new ResultAlleleResponse();
                    response.setAlleleValue(allele.getAlleleValue());
                    response.setAllelePosition(allele.getAllelePosition());
                    response.setAlleleStatus(allele.getAlleleStatus());

                    LocusResponse locusResponse = new LocusResponse();
                    locusResponse.setLocusId(allele.getLocus().getLocusId());
                    locusResponse.setLocusName(allele.getLocus().getLocusName());
                    response.setLocusResponse(locusResponse);

                    return response;
                });

        when(resultAlleleRepository.countBySample_SampleIdAndAlleleStatus(sampleId, AlleleStatus.INVALID))
                .thenReturn(3);

        resultAlleleService.createAllelePair(request, sampleId, locusId);

        assertEquals(SampleStatus.REJECTED, sample.getSampleStatus());
    }


    @Test
    void testCreateAllelePair_SampleNotFound() {
        long sampleId = 1L;
        long locusId = 2L;
        AllelePairRequest request = new AllelePairRequest();
        request.setAllele1(10.5);
        request.setAllele2(11.5);
        request.setAlleleStatus(AlleleStatus.INVALID);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                resultAlleleService.createAllelePair(request, sampleId, locusId)
        );
        assertEquals(ErrorCodeUser.SAMPLE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testCreateAllelePair_SampleRejected() {
        long sampleId = 1L;
        long locusId = 2L;
        AllelePairRequest request = new AllelePairRequest();
        request.setAllele1(10.5);
        request.setAllele2(11.5);
        request.setAlleleStatus(AlleleStatus.INVALID);

        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.REJECTED);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                resultAlleleService.createAllelePair(request, sampleId, locusId)
        );
        assertEquals("Mẫu này đã bị từ chối.", ex.getMessage());
    }

    @Test
    void testGetAllAlleleOfSample_PatientNotFound() {
        long patientId = 1L;
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                resultAlleleService.getAllAlleleOfSample(patientId)
        );
        assertEquals(ErrorCodeUser.PATIENT_INFO_NOT_EXISTS, ex.getErrorCode());
    }
}
