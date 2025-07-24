package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import swp.project.adn_backend.dto.request.sample.SampleRequest;
import swp.project.adn_backend.dto.response.sample.*;
import swp.project.adn_backend.dto.response.appointment.AppointmentResponse.KitAppointmentResponse;
import swp.project.adn_backend.dto.response.appointment.AppointmentResponse.ServiceAppointmentResponse;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.*;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.AllSampleResponseMapper;
import swp.project.adn_backend.mapper.AppointmentMapper;
import swp.project.adn_backend.mapper.SampleMapper;
import swp.project.adn_backend.mapper.StaffMapper;
import swp.project.adn_backend.repository.*;
import swp.project.adn_backend.service.registerServiceTestService.AppointmentService;
import swp.project.adn_backend.service.sample.SampleService;
import swp.project.adn_backend.service.slot.StaffAssignmentTracker;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SampleServiceTest {

    @Mock SampleRepository sampleRepository;
    @Mock SampleMapper sampleMapper;
    @Mock PatientRepository patientRepository;
    @Mock StaffRepository staffRepository;
    @Mock ServiceTestRepository serviceTestRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock StaffMapper staffMapper;
    @Mock AllSampleResponseMapper allSampleResponseMapper;
    @Mock AppointmentMapper appointmentMapper;
    @Mock StaffAssignmentTracker staffAssignmentTracker;
    @Mock AppointmentService appointmentService;
    @Mock SlotRepository slotRepository;
    @Mock Authentication authentication;
    @Mock Jwt jwt;

    @InjectMocks
    SampleService sampleService;

    @BeforeEach
    void setUp() {
        sampleService = new SampleService(
                sampleRepository, sampleMapper, patientRepository, staffRepository,
                serviceTestRepository, appointmentRepository, staffMapper,
                allSampleResponseMapper, appointmentMapper, staffAssignmentTracker,
                appointmentService, slotRepository
        );
    }

    @Test
    void testCollectSample_Success() {
        long patientId = 1L, serviceId = 2L, appointmentId = 3L, staffId = 4L;
        SampleRequest sampleRequest = mock(SampleRequest.class);
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setRole("COLLECTOR");
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        Kit kit = new Kit();
        kit.setQuantity(5);
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setKit(kit);
        Sample sample = new Sample();
        SampleResponse sampleResponse = new SampleResponse();

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        when(sampleMapper.toSample(sampleRequest)).thenReturn(sample);
        when(sampleRepository.save(any(Sample.class))).thenReturn(sample);
        when(sampleMapper.toSampleResponse(sample)).thenReturn(sampleResponse);

        SampleResponse result = sampleService.collectSample(patientId, serviceId, appointmentId, sampleRequest, authentication);

        assertEquals(sampleResponse, result);
        assertEquals(PatientStatus.SAMPLE_COLLECTED, patient.getPatientStatus());
        assertEquals(4, kit.getQuantity());
        assertEquals(SampleStatus.COLLECTED, sample.getSampleStatus());
        assertEquals(patient, sample.getPatient());
        assertEquals(staff, sample.getStaff());
        assertEquals(appointment, sample.getAppointment());
        assertEquals(kit, sample.getKit());
        assertNotNull(sample.getSampleCode());
        assertEquals(LocalDate.now(), sample.getCollectionDate());
    }

    @Test
    void testCollectSampleAtHome_Success() {
        long patientId = 1L, serviceId = 2L, appointmentId = 3L, staffId = 4L;
        SampleRequest sampleRequest = mock(SampleRequest.class);
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setRole("COLLECTOR");
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        Kit kit = new Kit();
        kit.setQuantity(5);
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setKit(kit);
        Sample sample = new Sample();
        SampleResponse sampleResponse = new SampleResponse();

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(staffRepository.findAll()).thenReturn(List.of(staff));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        when(sampleMapper.toSample(sampleRequest)).thenReturn(sample);
        when(sampleRepository.save(any(Sample.class))).thenReturn(sample);
        when(sampleMapper.toSampleResponse(sample)).thenReturn(sampleResponse);

        SampleResponse result = sampleService.collectSampleAtHome(patientId, serviceId, appointmentId, sampleRequest, authentication);

        assertEquals(sampleResponse, result);
        assertEquals(PatientStatus.SAMPLE_COLLECTED, patient.getPatientStatus());
        assertEquals(patient, sample.getPatient());
        assertEquals(staff, sample.getStaff());
        assertEquals(appointment, sample.getAppointment());
        assertEquals(kit, sample.getKit());
        assertNotNull(sample.getSampleCode());
        assertEquals(LocalDate.now(), sample.getCollectionDate());
    }

    @Test
    void testGetAllSampleOfPatient_RoleBasedFiltering() {
        long staffId = 10L, appointmentId = 20L;

        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setRole("LAB_TECHNICIAN");

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);

        // Tạo Samples
        Sample receivedSample = new Sample();
        receivedSample.setSampleStatus(SampleStatus.RECEIVED);

        Sample collectedSample = new Sample();
        collectedSample.setSampleStatus(SampleStatus.COLLECTED);

        Sample rejectedSample = new Sample();
        rejectedSample.setSampleStatus(SampleStatus.REJECTED);

        appointment.setSampleList(Arrays.asList(receivedSample, collectedSample, rejectedSample));

        // Mock ServiceTest và Kit
        ServiceTest serviceTest = new ServiceTest();
        Kit kit = new Kit();
        serviceTest.setKit(kit);

        appointment.setServices(serviceTest); // FIX: tránh getServices() null

        // Mock JWT + repositories
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // Mock mappers
        when(allSampleResponseMapper.toStaffSampleResponse(any())).thenReturn(new StaffSampleResponse());
        when(allSampleResponseMapper.toPatientSampleResponse(any())).thenReturn(new PatientSampleResponse());
        when(appointmentMapper.toAppointmentSampleResponse(any())).thenReturn(mock(AppointmentSampleResponse.class));
        when(appointmentMapper.toKitAppointmentResponse(any())).thenReturn(mock(KitAppointmentResponse.class));
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(mock(ServiceAppointmentResponse.class));
        when(sampleMapper.toSampleResponse(receivedSample)).thenReturn(mock(SampleResponse.class));

        // Call service
        List<AllSampleResponse> result = sampleService.getAllSampleOfPatient(authentication, appointmentId);

        // Verify only 1 sample is returned (RECEIVED status for LAB_TECHNICIAN)
        assertEquals(1, result.size());
    }



    @Test
    void testCollectSample_ThrowsOnMissingEntities() {
        long patientId = 1L, serviceId = 2L, appointmentId = 3L, staffId = 4L;
        SampleRequest sampleRequest = mock(SampleRequest.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);

        // Staff not found
        when(staffRepository.findById(staffId)).thenReturn(Optional.empty());
        AppException ex1 = assertThrows(AppException.class, () ->
                sampleService.collectSample(patientId, serviceId, appointmentId, sampleRequest, authentication));
        assertEquals(ErrorCodeUser.STAFF_NOT_EXISTED, ex1.getErrorCode());

        // Patient not found
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());
        AppException ex2 = assertThrows(AppException.class, () ->
                sampleService.collectSample(patientId, serviceId, appointmentId, sampleRequest, authentication));
        assertEquals(ErrorCodeUser.PATIENT_INFO_NOT_EXISTS, ex2.getErrorCode());

        // Appointment not found
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());
        AppException ex3 = assertThrows(AppException.class, () ->
                sampleService.collectSample(patientId, serviceId, appointmentId, sampleRequest, authentication));
        assertEquals(ErrorCodeUser.APPOINTMENT_NOT_EXISTS, ex3.getErrorCode());

        // Service not found
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.empty());
        AppException ex4 = assertThrows(AppException.class, () ->
                sampleService.collectSample(patientId, serviceId, appointmentId, sampleRequest, authentication));
        assertEquals(ErrorCodeUser.SERVICE_NOT_EXISTS, ex4.getErrorCode());
    }

    @Test
    void testUpdateSampleStatus_InvalidTransition() {
        long sampleId = 1L, appointmentId = 2L;
        SampleRequest sampleRequest = mock(SampleRequest.class);
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.IN_TRANSIT);
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(sampleRequest.getSampleStatus()).thenReturn(SampleStatus.COLLECTED);

        AppException ex = assertThrows(AppException.class, () ->
                sampleService.updateSampleStatus(sampleId, appointmentId, sampleRequest));
        assertEquals(ErrorCodeUser.INVALID_SAMPLE_STATUS_TRANSITION, ex.getErrorCode());
    }

    @Test
    void testCollectSample_ThrowsWhenKitQuantityZero() {
        long patientId = 1L, serviceId = 2L, appointmentId = 3L, staffId = 4L;
        SampleRequest sampleRequest = mock(SampleRequest.class);
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        Kit kit = new Kit();
        kit.setQuantity(0);
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setKit(kit);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                sampleService.collectSample(patientId, serviceId, appointmentId, sampleRequest, authentication));
        assertEquals("Cơ sở đã hết số lượng kit", ex.getMessage());
    }

    @Test
    void testUpdateSampleStatus_CollectedToInTransit() {
        long sampleId = 1L, appointmentId = 2L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.COLLECTED);

        Patient patient = new Patient();
        patient.setFullName("John Doe");
        sample.setPatient(patient);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatients(List.of(patient));

        SampleRequest sampleRequest = mock(SampleRequest.class);
        when(sampleRequest.getSampleStatus()).thenReturn(SampleStatus.IN_TRANSIT);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(staffRepository.findAll()).thenReturn(Collections.emptyList());

        sampleService.updateSampleStatus(sampleId, appointmentId, sampleRequest);

        assertEquals(SampleStatus.IN_TRANSIT, sample.getSampleStatus());
        assertEquals("Mẫu của John Doe đang vận chuyển đến phòng xét nghiệm", appointment.getNote());
    }

    @Test
    void testUpdateSampleStatus_ToReceived_AssignsLabAndUpdatesPatients() {
        long sampleId = 1L, appointmentId = 2L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.IN_TRANSIT);

        Patient patient1 = new Patient();
        patient1.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);
        Patient patient2 = new Patient();
        patient2.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatients(List.of(patient1, patient2));

        SampleRequest sampleRequest = mock(SampleRequest.class);
        when(sampleRequest.getSampleStatus()).thenReturn(SampleStatus.RECEIVED);

        Staff labTech1 = new Staff();
        labTech1.setStaffId(10L);
        labTech1.setRole("LAB_TECHNICIAN");
        Staff labTech2 = new Staff();
        labTech2.setStaffId(11L);
        labTech2.setRole("LAB_TECHNICIAN");

        List<Staff> allStaff = List.of(labTech1, labTech2);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(staffRepository.findAll()).thenReturn(allStaff);
        when(staffAssignmentTracker.getNextIndex(2)).thenReturn(1);

        sampleService.updateSampleStatus(sampleId, appointmentId, sampleRequest);

        assertEquals(SampleStatus.RECEIVED, sample.getSampleStatus());
        assertEquals(PatientStatus.IN_ANALYSIS, patient1.getPatientStatus());
        assertEquals(PatientStatus.IN_ANALYSIS, patient2.getPatientStatus());
        assertEquals("Phòng xét nghiệm đã nhận mẫu", appointment.getNote());
        assertEquals(labTech2, appointment.getStaff());
    }

    @Test
    void testUpdateSampleStatus_ToCompleted_UpdatesNote() {
        long sampleId = 1L, appointmentId = 2L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.TESTING);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatients(Collections.emptyList());

        SampleRequest sampleRequest = mock(SampleRequest.class);
        when(sampleRequest.getSampleStatus()).thenReturn(SampleStatus.COMPLETED);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(staffRepository.findAll()).thenReturn(Collections.emptyList());

        sampleService.updateSampleStatus(sampleId, appointmentId, sampleRequest);

        assertEquals(SampleStatus.COMPLETED, sample.getSampleStatus());
        assertEquals("Đã xét nghiệm xong", appointment.getNote());
    }

    @Test
    void testUpdateSampleStatus_ToReceived_NoLabTechnicians() {
        long sampleId = 1L, appointmentId = 2L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.IN_TRANSIT);

        Patient patient = new Patient();
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatients(List.of(patient));

        SampleRequest sampleRequest = mock(SampleRequest.class);
        when(sampleRequest.getSampleStatus()).thenReturn(SampleStatus.RECEIVED);

        Staff nonLabStaff = new Staff();
        nonLabStaff.setStaffId(20L);
        nonLabStaff.setRole("COLLECTOR");

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(staffRepository.findAll()).thenReturn(List.of(nonLabStaff));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                sampleService.updateSampleStatus(sampleId, appointmentId, sampleRequest));
        assertEquals("Không có nhân viên phòng lab", ex.getMessage());
    }

    @Test
    void testUpdateSampleStatus_SampleNotExists() {
        long sampleId = 1L, appointmentId = 2L;
        SampleRequest sampleRequest = mock(SampleRequest.class);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                sampleService.updateSampleStatus(sampleId, appointmentId, sampleRequest));
        assertEquals(ErrorCodeUser.SAMPLE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testUpdateSampleStatus_NullPatientInNoteUpdate() {
        long sampleId = 1L, appointmentId = 2L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.COLLECTED);
        sample.setPatient(null);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatients(Collections.emptyList());

        SampleRequest sampleRequest = mock(SampleRequest.class);
        when(sampleRequest.getSampleStatus()).thenReturn(SampleStatus.IN_TRANSIT);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(staffRepository.findAll()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> sampleService.updateSampleStatus(sampleId, appointmentId, sampleRequest));
        assertNull(appointment.getNote());
    }

    @Test
    void testDeleteSample_StatusIsRejectedAndPersisted() {
        long sampleId = 123L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.COLLECTED);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));

        sampleService.deleteSample(sampleId);

        assertEquals(SampleStatus.REJECTED, sample.getSampleStatus());
        verify(sampleRepository).findById(sampleId);
    }

    @Test
    void testDeleteSample_IdempotentOnRepeatedCalls() {
        long sampleId = 456L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.COLLECTED);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));

        sampleService.deleteSample(sampleId);
        assertEquals(SampleStatus.REJECTED, sample.getSampleStatus());

        // Call again, should not throw or change status further
        sampleService.deleteSample(sampleId);
        assertEquals(SampleStatus.REJECTED, sample.getSampleStatus());

        verify(sampleRepository, times(2)).findById(sampleId);
    }

    @Test
    void testDeleteSample_AlreadyRejectedSample() {
        long sampleId = 789L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.REJECTED);

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));

        assertDoesNotThrow(() -> sampleService.deleteSample(sampleId));
        assertEquals(SampleStatus.REJECTED, sample.getSampleStatus());
        verify(sampleRepository).findById(sampleId);
    }

    @Test
    void testDeleteSample_ThrowsCorrectExceptionType() {
        long sampleId = 999L;
        when(sampleRepository.findById(sampleId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> sampleService.deleteSample(sampleId));
        assertEquals(ErrorCodeUser.SAMPLE_NOT_EXISTS, ex.getErrorCode());
        verify(sampleRepository).findById(sampleId);
    }

    @Test
    void testDeleteSample_RepositoryFailure() {
        long sampleId = 1001L;
        when(sampleRepository.findById(sampleId)).thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> sampleService.deleteSample(sampleId));
        assertEquals("DB error", ex.getMessage());
        verify(sampleRepository).findById(sampleId);
    }

    @Test
    void testDeleteSample_NoUnintendedFieldModification() {
        long sampleId = 2002L;
        Sample sample = new Sample();
        sample.setSampleId(sampleId);
        sample.setSampleStatus(SampleStatus.COLLECTED);
        sample.setSampleCode("ABC123");
        sample.setSampleType("Blood");
        sample.setCollectionDate(LocalDate.of(2024, 6, 1));

        String originalCode = sample.getSampleCode();
        String originalType = sample.getSampleType();
        LocalDate originalDate = sample.getCollectionDate();

        when(sampleRepository.findById(sampleId)).thenReturn(Optional.of(sample));

        sampleService.deleteSample(sampleId);

        assertEquals(SampleStatus.REJECTED, sample.getSampleStatus());
        assertEquals(originalCode, sample.getSampleCode());
        assertEquals(originalType, sample.getSampleType());
        assertEquals(originalDate, sample.getCollectionDate());
        verify(sampleRepository).findById(sampleId);
    }
}
