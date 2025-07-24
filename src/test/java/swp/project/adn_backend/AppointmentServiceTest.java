package swp.project.adn_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.ap.internal.util.Services;
import org.mockito.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;
import swp.project.adn_backend.dto.InfoDTO.AppointmentInfoForManagerDTO;
import swp.project.adn_backend.dto.request.appointment.AppointmentRequest;
import swp.project.adn_backend.dto.request.payment.CreatePaymentRequest;
import swp.project.adn_backend.dto.request.payment.PaymentRequest;
import swp.project.adn_backend.dto.request.roleRequest.PatientRequest;
import swp.project.adn_backend.dto.request.roleRequest.UserPhoneRequest;
import swp.project.adn_backend.dto.request.serviceRequest.PriceListRequest;
import swp.project.adn_backend.dto.request.updateRequest.UpdateServiceTestRequest;
import swp.project.adn_backend.dto.response.appointment.AppointmentResponse.*;
import swp.project.adn_backend.dto.response.appointment.AppointmentResponse.appointmentResult.*;
import swp.project.adn_backend.dto.response.appointment.updateAppointmentStatus.UpdateAppointmentStatusResponse;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.*;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.AppointmentMapper;
import swp.project.adn_backend.repository.*;
import swp.project.adn_backend.service.registerServiceTestService.AppointmentService;
import swp.project.adn_backend.service.registerServiceTestService.EmailService;
import swp.project.adn_backend.service.registerServiceTestService.ServiceTestService;
import swp.project.adn_backend.service.result.ResultDetailsService;
import swp.project.adn_backend.service.roleService.PatientService;
import swp.project.adn_backend.service.slot.StaffAssignmentTracker;
import swp.project.adn_backend.mapper.ResultDetailsMapper;

public class AppointmentServiceTest {
    @Spy
    @InjectMocks
    private AppointmentService appointmentService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<AppointmentInfoForManagerDTO> query;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceTestRepository serviceTestRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private PriceListRepository priceListRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private PatientService patientService;
    @Mock
    private EmailService emailService;
    @Mock
    private ServiceTestService serviceTestService;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private Authentication authentication;
    @Mock
    private KitDeliveryStatusRepository kitDeliveryStatusRepository;
    @Mock
    private StaffAssignmentTracker staffAssignmentTracker;
    @Mock
    private CivilServiceRepository civilServiceRepository;
    @Mock
    private Jwt jwt;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private ResultDetailRepository resultDetailRepository;
    @Mock
    private ResultRepository resultRepository;
    @Mock
    private ResultDetailsMapper resultDetailsMapper;
    @Mock
    private ResultDetailsService resultDetailService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testBookAppointmentAtCenter_success() {
        // Arrange
        long slotId = 1L, locationId = 2L, serviceId = 3L, priceId = 4L;
        Authentication auth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(10L);

        // Mock user
        Users user = new Users();
        user.setUserId(10L);
        user.setEmail("test@example.com");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        // Mock slot with staff
        Slot slot = new Slot();
        slot.setSlotId(slotId);
        slot.setSlotStatus(SlotStatus.AVAILABLE);
        slot.setSlotDate(LocalDate.of(2025, 7, 11));
        Staff staff = new Staff();
        staff.setStaffId(99L);
        staff.setRole("SAMPLE_COLLECTOR");
        slot.setStaff(List.of(staff));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // Mock service
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(serviceId);
        serviceTest.setDiscounts(List.of());
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        // Mock price
        PriceList priceList = new PriceList();
        priceList.setPrice(100000);
        when(priceListRepository.findById(priceId)).thenReturn(Optional.of(priceList));

        // Mock location
        Location location = new Location();
        location.setLocationId(locationId);
        location.setAddressLine("123 Main");
        location.setDistrict("District A");
        location.setCity("City Z");
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));

        // Mock appointment
        AppointmentRequest appointmentRequest = new AppointmentRequest();
        Appointment appointment = new Appointment();
        appointment.setSlot(slot);
        appointment.setUsers(user);
        when(appointmentMapper.toAppointment(appointmentRequest)).thenReturn(appointment);

        // Mock patients
        List<PatientRequest> patientRequests = new ArrayList<>();
        List<Patient> patients = List.of(new Patient());
        when(patientService.registerServiceTest(anyList(), eq(user), eq(serviceTest))).thenReturn(patients);

        // Mock appointment save
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);

        // Mock payment request
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);

        // Mock all response mappers (these must return dummy data to prevent null)
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toSlotAppointmentResponse(any())).thenReturn(new SlotAppointmentResponse());
        when(appointmentMapper.toRoomAppointmentResponse(any())).thenReturn(new RoomAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toLocationAppointmentResponse(any())).thenReturn(new LocationAppointmentResponse());
        when(appointmentMapper.toPriceAppointmentResponse(any())).thenReturn(List.of(new PriceAppointmentResponse()));
        when(appointmentMapper.toPatientAppointmentService(any())).thenReturn(List.of(new PatientAppointmentResponse()));

        // Act
        AllAppointmentAtCenterResponse response = appointmentService.bookAppointmentAtCenter(
                appointmentRequest, auth, patientRequests, paymentRequest, slotId, locationId, serviceId, priceId
        );

        // Assert
        assertNotNull(response);
        verify(appointmentRepository).save(any(Appointment.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(emailService).sendAppointmentAtCenterDetailsEmail(eq("test@example.com"), any());
    }


    @Test
    void testGetHistory_ReturnsCorrectResponses() {
        // Mock JWT Authentication
        Jwt jwt = Jwt.withTokenValue("token")
                .claim("id", 1L)
                .header("alg", "none")
                .build();

        Authentication authentication = new JwtAuthenticationToken(jwt);

        // Mock user
        Users user = new Users();
        user.setUserId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Mock appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentType(AppointmentType.CENTER);
        appointment.setUsers(user);

        Slot slot = new Slot();
        Room room = new Room();
        room.setRoomName("Room A");
        slot.setRoom(room);
        appointment.setSlot(slot);

        Staff staff = new Staff();
        appointment.setStaff(staff);

        ServiceTest service = new ServiceTest();
        service.setServiceType(ServiceType.CIVIL);
        Kit kit = new Kit();
        service.setKit(kit);
        appointment.setServices(service);

        Location location = new Location();
        appointment.setLocation(location);

        Payment payment = new Payment();
        appointment.setPayments(List.of(payment));

        when(appointmentRepository.findByUsers_UserId(1L)).thenReturn(List.of(appointment));

        // Mock mapper responses
        when(appointmentMapper.toShowAppointmentResponse(appointment)).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(staff)).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toSlotAppointmentResponse(slot)).thenReturn(new SlotAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(service)).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toLocationAppointmentResponse(location)).thenReturn(new LocationAppointmentResponse());
        when(appointmentMapper.toPaymentAppointmentResponse(List.of(payment))).thenReturn(List.of(new PaymentAppointmentResponse()));
        when(appointmentMapper.toKitAppointmentResponse(kit)).thenReturn(new KitAppointmentResponse());

        // Run
        AllAppointmentResponse response = appointmentService.getHistory(authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getAllAppointmentAtCenterResponse().size());
        assertEquals(0, response.getAllAppointmentAtHomeResponse().size());

        RoomAppointmentResponse roomResponse = response.getAllAppointmentAtCenterResponse().get(0).getRoomAppointmentResponse();
        assertEquals("Room A", roomResponse.getRoomName());
    }

    @Test
    void testGetAllAppointmentsResultForManager_ReturnsResultsSuccessfully() {
        // GIVEN
        Long userId = 1L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(authentication.getPrincipal()).thenReturn(jwt);

        Users managerUser = new Users();
        managerUser.setUserId(userId);
        managerUser.setRoles(Set.of(Roles.MANAGER.name()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(managerUser));

        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.WAITING_MANAGER_APPROVAL);

        Result result = new Result();
        result.setResultStatus(ResultStatus.COMPLETED);
        ResultDetail detail = new ResultDetail();
        ResultLocus locus = new ResultLocus();
        detail.setResultLoci(List.of(locus));
        result.setResultDetail(detail);
        appointment.setResults(List.of(result));

        when(appointmentRepository.findAll()).thenReturn(List.of(appointment));

        // Mock mapping
        when(appointmentMapper.toPatientAppointmentService(any())).thenReturn(List.of());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toSampleAppointmentResponse(any())).thenReturn(List.of());
        when(appointmentMapper.toResultAppointmentResponse(any())).thenReturn(new ResultAppointmentResponse());
        when(appointmentMapper.toResultDetailAppointmentResponse(any())).thenReturn(new ResultDetailAppointmentResponse());
        when(appointmentMapper.toResultLocusAppointmentResponse(any())).thenReturn(new ResultLocusAppointmentResponse());

        // WHEN
        List<AllAppointmentResult> results = appointmentService.getAllAppointmentsResultForManager(authentication, 1L);

        // THEN
        assertNotNull(results);
        assertEquals(1, results.size());
        verify(userRepository).findById(userId);
        verify(appointmentRepository).findAll();
    }
    @Test
    void testGetAppointmentAtHomeToRecordResult_success() {
        Long userId = 10L;
        Long appointmentId = 1L;

        // Mock JWT and authentication
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        // Mock Staff
        Staff staff = new Staff();
        staff.setRole("STAFF_AT_HOME");
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));

        // Mock Appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);

        // Mock Patients
        Patient patient = new Patient();
        appointment.setPatients(List.of(patient));

        // Mock Services
        ServiceTest serviceTest = new ServiceTest();
        appointment.setServices(serviceTest);

        // Mock User and Staff for Appointment
        Users user = new Users();
        appointment.setUsers(user);
        appointment.setStaff(staff);

        // Mock Repositories
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // Mock Mapper Responses
        ShowAppointmentResponse show = new ShowAppointmentResponse();
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(show);
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toPatientAppointment(any())).thenReturn(new PatientAppointmentResponse());
        when(appointmentMapper.toPriceAppointmentResponse(anyList())).thenReturn(List.of(new PriceAppointmentResponse()));

        // Execute
        List<AllAppointmentAtCenterResponse> result = appointmentService.getAppointmentAtHomeToRecordResult(authentication, appointmentId);

        // Verify
        assertEquals(1, result.size());
        AllAppointmentAtCenterResponse response = result.get(0);
        assertNotNull(response.getShowAppointmentResponse());
        assertNotNull(response.getPatientAppointmentResponse());
        verify(staffRepository).findById(userId);
        verify(appointmentRepository).findById(appointmentId);
    }

    @Test
    void testGetAppointmentAtHomeToRecordResult_staffNotFound() {
        Long userId = 10L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> appointmentService.getAppointmentAtHomeToRecordResult(authentication, 1L));
        assertEquals(ErrorCodeUser.STAFF_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void testGetAppointmentAtHomeToRecordResult_staffWrongRole() {
        Long userId = 10L;
        Staff staff = new Staff();
        staff.setRole("CASHIER");

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> appointmentService.getAppointmentAtHomeToRecordResult(authentication, 1L));
        assertEquals("Chỉ có nhân viên thu mẫu tại nhà mới có thể lấy", ex.getMessage());
    }

    @Test
    void testAppointmentRefund_CenterAppointment_ShouldUpdateStatusAndNote() {
        // Arrange
        long appointmentId = 1L;

        Slot slot = new Slot();
        slot.setSlotStatus(SlotStatus.BOOKED);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setAppointmentType(AppointmentType.CENTER);
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setSlot(slot);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // Act
        appointmentService.appointmentRefund(appointmentId);

        // Assert
        assertEquals(AppointmentStatus.REFUND, appointment.getAppointmentStatus());
        assertEquals("vì lí do mẫu bị hư nên chúng tôi hoàn trả đơn cũng như tiền mong ví khách đăng kí lại để hổ trợ lấy mẫu lại ", appointment.getNote());
        assertEquals(SlotStatus.COMPLETED, appointment.getSlot().getSlotStatus());
    }
    @Test
    void testAppointmentRefund_NotFound_ShouldThrowException() {
        // Arrange
        long appointmentId = 99L;
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        // Act + Assert
        AppException exception = assertThrows(AppException.class, () -> appointmentService.appointmentRefund(appointmentId));
        assertEquals(ErrorCodeUser.APPOINTMENT_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void testAppointmentRefund_NotCenterAppointment_ShouldSkipSlotUpdate() {
        // Arrange
        long appointmentId = 2L;
        Slot slot = new Slot();
        slot.setSlotStatus(SlotStatus.BOOKED);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setAppointmentType(AppointmentType.HOME); // not CENTER
        appointment.setSlot(slot);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // Act
        appointmentService.appointmentRefund(appointmentId);

        // Assert
        assertEquals(AppointmentStatus.REFUND, appointment.getAppointmentStatus());
        assertEquals("vì lí do mẫu bị hư nên chúng tôi hoàn trả đơn cũng như tiền mong ví khách đăng kí lại để hổ trợ lấy mẫu lại ", appointment.getNote());
        assertEquals(SlotStatus.BOOKED, appointment.getSlot().getSlotStatus()); // unchanged
    }
    @Test
    void testAppointmentRefund_AppointmentNotFound() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            appointmentService.appointmentRefund(99L);
        });

        assertEquals(ErrorCodeUser.APPOINTMENT_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void testGetAppointmentAtHomeToRecordResult_appointmentNotFound() {
        Long userId = 10L;
        Staff staff = new Staff();
        staff.setRole("STAFF_AT_HOME");

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> appointmentService.getAppointmentAtHomeToRecordResult(authentication, 1L));
        assertEquals(ErrorCodeUser.APPOINTMENT_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testGetAppointmentAtHome_success() {
        // Arrange
        Long staffId = 1L;

        Appointment appointment = new Appointment();
        appointment.setAppointmentType(AppointmentType.HOME);
        appointment.setAppointmentStatus(AppointmentStatus.PENDING);

        ServiceTest service = new ServiceTest();
        service.setServiceType(ServiceType.CIVIL);
        Kit kit = new Kit();
        service.setKit(kit);
        appointment.setServices(service);

        Users user = new Users();
        appointment.setUsers(user);

        List<Appointment> appointmentList = List.of(appointment);

        Staff staff = new Staff();
        staff.setAppointments(appointmentList);

        ShowAppointmentResponse show = new ShowAppointmentResponse();
        ServiceAppointmentResponse serviceResponse = new ServiceAppointmentResponse();
        KitAppointmentResponse kitResponse = new KitAppointmentResponse();
        List<Payment> payments = new ArrayList<>();
        appointment.setPayments(payments);
        List<PaymentAppointmentResponse> paymentResponse = new ArrayList<>();
        UserAppointmentResponse userAppointmentResponse = new UserAppointmentResponse();

        // Mocking
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(appointmentMapper.toShowAppointmentResponse(appointment)).thenReturn(show);
        when(appointmentMapper.toServiceAppointmentResponse(service)).thenReturn(serviceResponse);
        when(appointmentMapper.toKitAppointmentResponse(kit)).thenReturn(kitResponse);
        when(appointmentMapper.toPaymentAppointmentResponse(payments)).thenReturn(paymentResponse);
        when(appointmentMapper.toUserAppointmentResponse(user)).thenReturn(userAppointmentResponse);

        // Act
        List<AllAppointmentAtHomeResponse> result = appointmentService.getAppointmentAtHome(authentication);

        // Assert
        assertEquals(1, result.size());
        assertEquals(show, result.get(0).getShowAppointmentResponse());
        assertEquals(serviceResponse, result.get(0).getServiceAppointmentResponses().get(0));
        assertEquals(kitResponse, result.get(0).getKitAppointmentResponse());
        assertEquals(userAppointmentResponse, result.get(0).getUserAppointmentResponses());
    }


    @Test
    void testGetAppointmentAtHome_staffNotFound() {
        // Arrange
        Long staffId = 1L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () ->
                appointmentService.getAppointmentAtHome(authentication));

        assertEquals(ErrorCodeUser.STAFF_NOT_EXISTED, exception.getErrorCode());
    }
    @Test
    void testGetAppointmentAtHomeByStaffId_centerAppointment_success() {
        // Arrange
        Long staffId = 1L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);

        // Mock Staff
        Staff staff = new Staff();
        staff.setRole("STAFF_AT_HOME");

        // Mock Slot + Room
        Room room = new Room();
        room.setRoomName("Phòng A");

        Slot slot = new Slot();
        slot.setRoom(room);

        // Mock Appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentType(AppointmentType.CENTER);
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setSlot(slot);

        // Mock Patient with status SAMPLE_COLLECTED
        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);
        appointment.setPatients(List.of(patient));

        // Mock Service
        ServiceTest appointmentServiceObj = new ServiceTest();
        appointment.setServices(appointmentServiceObj);

        // Add to staff
        staff.setAppointments(List.of(appointment));

        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        // Mock Mapper responses
        ShowAppointmentResponse show = new ShowAppointmentResponse();
        ServiceAppointmentResponse serviceResp = new ServiceAppointmentResponse();
        PatientAppointmentResponse patientResp = new PatientAppointmentResponse();

        when(appointmentMapper.toShowAppointmentResponse(appointment)).thenReturn(show);
        when(appointmentMapper.toServiceAppointmentResponse(appointmentServiceObj)).thenReturn(serviceResp);
        when(appointmentMapper.toPatientAppointment(patient)).thenReturn(patientResp);

        // Act
        AllAppointmentResponse response = appointmentService.getAppointmentAtHomeByStaffId(authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getAllAppointmentAtCenterResponse().size());

        AllAppointmentAtCenterResponse center = response.getAllAppointmentAtCenterResponse().get(0);
        assertEquals(show, center.getShowAppointmentResponse());
        assertEquals(1, center.getServiceAppointmentResponses().size());
        assertEquals(serviceResp, center.getServiceAppointmentResponses().get(0));
        assertEquals(1, center.getPatientAppointmentResponse().size());
        assertEquals(patientResp, center.getPatientAppointmentResponse().get(0));
    }
    @Test
    void testGetAppointmentAtHomeByStaffId_success() {
        // Given
        Long staffId = 1L;

        Staff staff = new Staff();
        staff.setRole("STAFF_AT_HOME");

        Appointment appointment1 = new Appointment();
        appointment1.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment1.setAppointmentType(AppointmentType.HOME);

        ServiceTest service = new ServiceTest();
        service.setServiceType(ServiceType.CIVIL);
        appointment1.setServices(service);

        Slot slot = new Slot();
        Room room = new Room();
        room.setRoomName("Room A");
        slot.setRoom(room);
        appointment1.setSlot(slot);

        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);
        appointment1.setPatients(List.of(patient));

        staff.setAppointments(List.of(appointment1));

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toPatientAppointment(any())).thenReturn(new PatientAppointmentResponse());

        // When
        AllAppointmentResponse result = appointmentService.getAppointmentAtHomeByStaffId(authentication);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getAllAppointmentAtCenterResponse().size());
        assertEquals(1, result.getAllAppointmentAtHomeResponse().size());
    }

    @Test
    void testGetAppointmentAtHomeByStaffId_staffNotFound_shouldThrow() {
        // Given
        Long staffId = 1L;

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.empty());

        // Then
        assertThrows(AppException.class, () -> {
            // When
            appointmentService.getAppointmentAtHomeByStaffId(authentication);
        });
    }

    @Test
    void testGetAppointmentAtHomeByStaffId_notStaffAtHome_shouldThrow() {
        // Given
        Long staffId = 1L;
        Staff staff = new Staff();
        staff.setRole("MANAGER");

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        // Then
        assertThrows(RuntimeException.class, () -> {
            // When
            appointmentService.getAppointmentAtHomeByStaffId(authentication);
        });
    }


    @Test
    void testGetAllAppointmentsResultForManager_success() {
        // Arrange: Create a mocked Jwt object
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("id")).thenReturn(10L);

        // Create JwtAuthenticationToken
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);

        // 🔧 Create mock user with MANAGER role
        Users user = new Users();
        user.setUserId(10L);


        user.setRoles(Set.of(Roles.MANAGER.name()));  // ✅ Make sure this matches your real structure

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        // Mock Appointment and Result data
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.WAITING_MANAGER_APPROVAL);

        Result result = new Result();
        result.setResultStatus(ResultStatus.COMPLETED);

        ResultDetail resultDetail = new ResultDetail();
        resultDetail.setResultLoci(Collections.emptyList());

        result.setResultDetail(resultDetail);
        appointment.setResults(List.of(result));

        // Mock mapper responses
        when(appointmentRepository.findAll()).thenReturn(List.of(appointment));
        when(appointmentMapper.toResultAppointmentResponse(any())).thenReturn(new ResultAppointmentResponse());
        when(appointmentMapper.toResultDetailAppointmentResponse(any())).thenReturn(new ResultDetailAppointmentResponse());
        when(appointmentMapper.toResultLocusAppointmentResponse(any())).thenReturn(new ResultLocusAppointmentResponse());

        // Act
        List<AllAppointmentResult> results = appointmentService.getAllAppointmentsResultForManager(authentication, 1L);

        // Assert
        assertEquals(1, results.size());
    }

    @Test
    void testPayAppointment_success() {
        long paymentId = 1L;
        long appointmentId = 2L;
        long serviceId = 3L;

        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.VN_PAY);
        payment.setAmount(1000);
        Appointment appointment = new Appointment();
        ServiceTest service = new ServiceTest();
        service.setServiceId(serviceId);
        service.setServiceName("DNA Test");
        appointment.setServices(service);
        payment.setAppointment(appointment);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // Mock createPayment to avoid saving invoice during test
        doReturn(new CreatePaymentRequest())
                .when(appointmentService)
                .createPayment(paymentId, serviceId);

        // Run method
        appointmentService.payAppointment(paymentId, appointmentId);

        // Assertions
        assertEquals("Đã thanh toán", appointment.getNote());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
    }
    @Test
    void testPayAppointment_paymentNotFound() {
        long paymentId = 1L;
        long appointmentId = 2L;

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            appointmentService.payAppointment(paymentId, appointmentId);
        });

        assertEquals(ErrorCodeUser.PAYMENT_INFO_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void testPayAppointment_appointmentNotFound() {
        long paymentId = 1L;
        long appointmentId = 2L;

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            appointmentService.payAppointment(paymentId, appointmentId);
        });

        assertEquals(ErrorCodeUser.APPOINTMENT_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void testBookAppointmentAtCenter_slotNotFound() {
        // Arrange
        long slotId = 1L, locationId = 2L, serviceId = 3L, priceId = 4L;
        Authentication auth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(10L);

        // Mock user to avoid USER_NOT_EXISTED
        Users user = new Users();
        user.setUserId(10L);
        user.setEmail("test@example.com");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(new ServiceTest()));
        when(priceListRepository.findById(priceId)).thenReturn(Optional.of(new PriceList()));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(new Location()));

        AppointmentRequest appointmentRequest = new AppointmentRequest();
        List<PatientRequest> patientRequests = new ArrayList<>();
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            appointmentService.bookAppointmentAtCenter(
                    appointmentRequest, auth, patientRequests, paymentRequest,
                    slotId, locationId, serviceId, priceId
            );
        });

        assertEquals(ErrorCodeUser.SLOT_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void testBookAppointmentAtHome_serviceNotFound() {
        // Arrange
        long serviceId = 1L;
        long priceId = 2L;

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(10L);

        // Mock user to avoid USER_NOT_EXISTED
        Users user = new Users();
        user.setUserId(10L);
        user.setAddress("123 Street");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        // 👇 Do NOT mock serviceTestRepository => simulate service not found
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.empty());

        // Mock price to prevent it from throwing error
        PriceList priceList = new PriceList();
        priceList.setPriceId(priceId);
        priceList.setPrice(100000);
        priceList.setTime("Morning");
        when(priceListRepository.findById(priceId)).thenReturn(Optional.of(priceList));

        AppointmentRequest appointmentRequest = new AppointmentRequest();
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            appointmentService.bookAppointmentAtHome(
                    appointmentRequest,
                    authentication,
                    new ArrayList<>(),
                    paymentRequest,
                    serviceId,
                    priceId
            );
        });

        assertEquals(ErrorCodeUser.SERVICE_NOT_EXISTS, exception.getErrorCode());
    }


    @Test
    void testBookAppointmentAtHome_success() {
        // Arrange
        long serviceId = 1L;
        long priceId = 2L;

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(10L);

        Users user = new Users();
        user.setUserId(10L);
        user.setAddress("123 Street");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        ServiceTest serviceTest = new ServiceTest();
        Kit kit = new Kit();
        serviceTest.setKit(kit);
        serviceTest.setDiscounts(new ArrayList<>());
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        PriceList priceList = new PriceList();
        priceList.setPriceId(priceId);
        priceList.setPrice(100000);
        priceList.setTime("Morning");
        when(priceListRepository.findById(priceId)).thenReturn(Optional.of(priceList));

        // Mock staff list
        List<Staff> staffList = new ArrayList<>();
        Staff staff = new Staff();
        staff.setRole("STAFF_AT_HOME");
        staffList.add(staff);
        when(staffRepository.findAll()).thenReturn(staffList);

        // Mock appointment and mapper
        AppointmentRequest appointmentRequest = new AppointmentRequest();
        Appointment appointment = new Appointment();
        appointment.setUsers(user);
        when(appointmentMapper.toAppointment(appointmentRequest)).thenReturn(appointment);

        List<Patient> patients = List.of(new Patient());
        when(patientService.registerServiceTest(any(), eq(user), eq(serviceTest))).thenReturn(patients);

        when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);
        when(kitDeliveryStatusRepository.save(any())).thenReturn(null);
        when(paymentRepository.save(any())).thenReturn(null);

        // Mock all appointmentMapper responses
        when(appointmentMapper.toUserAppointmentResponse(user)).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toKitAppointmentResponse(any())).thenReturn(new KitAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toPatientAppointmentService(anyList()))
                .thenReturn(List.of(new PatientAppointmentResponse()));

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);

        // Act
        AllAppointmentAtHomeResponse response = appointmentService.bookAppointmentAtHome(
                appointmentRequest,
                authentication,
                new ArrayList<>(),
                paymentRequest,
                serviceId,
                priceId
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getUserAppointmentResponse().size()); // ✅ Fixed assertion
        verify(appointmentRepository).save(any(Appointment.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(kitDeliveryStatusRepository).save(any());
    }

    @Test
    void testConfirmAppointmentAtHome_success() {
        // Arrange
        long appointmentId = 1L;
        long userId = 10L;
        long serviceId = 20L;

        // User mock
        Users user = new Users();
        user.setUserId(userId);
        user.setEmail("test@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Kit + ServiceTest mock
        Kit kit = new Kit();
        kit.setQuantity(5); // must > 0
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setKit(kit);
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        // Appointment mock
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setUsers(user);
        appointment.setAppointmentStatus(AppointmentStatus.PENDING);
        KitDeliveryStatus deliveryStatus = new KitDeliveryStatus();
        appointment.setKitDeliveryStatus(deliveryStatus);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // Mapper return mock objects
        UpdateAppointmentStatusResponse statusResponse = new UpdateAppointmentStatusResponse();
        when(appointmentMapper.toUpdateAppointmentStatusResponse(appointment)).thenReturn(statusResponse);
        when(appointmentMapper.toShowAppointmentResponse(appointment)).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toKitAppointmentResponse(kit)).thenReturn(new KitAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(user)).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(serviceTest)).thenReturn(new ServiceAppointmentResponse());

        // Act
        UpdateAppointmentStatusResponse result = appointmentService.ConfirmAppointmentAtHome(
                appointmentId, userId, serviceId
        );

        // Assert
        assertNotNull(result);
        assertEquals(statusResponse, result);
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getAppointmentStatus());
        assertEquals(DeliveryStatus.PENDING, appointment.getKitDeliveryStatus().getDeliveryStatus());
        assertEquals(4, serviceTest.getKit().getQuantity()); // 5 - 1

        // Verify interactions
        verify(userRepository).findById(userId);
        verify(serviceTestRepository).findById(serviceId);
        verify(appointmentRepository).findById(appointmentId);
        verify(emailService).sendAppointmentHomeDetailsEmail(eq(user.getEmail()), any());
    }

    @Test
    void testGetAppointmentByStaffId_success() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(1L);

        // Staff mock
        Staff staff = new Staff();
        staff.setStaffId(1L);
        staff.setRole("LAB_TECHNICIAN");

        // Appointment CENTER
        Appointment centerAppointment = new Appointment();
        centerAppointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        centerAppointment.setAppointmentType(AppointmentType.CENTER);

        Room room = new Room();
        room.setRoomName("Phòng A");
        Slot slot = new Slot();
        slot.setRoom(room);
        centerAppointment.setSlot(slot);

        ServiceTest serviceCenter = new ServiceTest();
        Kit kit = new Kit();
        serviceCenter.setKit(kit);
        centerAppointment.setServices(serviceCenter);

        Patient patient1 = new Patient();
        List<Patient> patientsCenter = List.of(patient1);
        centerAppointment.setPatients(patientsCenter);

        // Appointment HOME
        Appointment homeAppointment = new Appointment();
        homeAppointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        homeAppointment.setAppointmentType(AppointmentType.HOME);

        ServiceTest serviceHome = new ServiceTest();
        serviceHome.setServiceType(ServiceType.CIVIL);
        Kit kitHome = new Kit();
        serviceHome.setKit(kitHome);
        homeAppointment.setServices(serviceHome);

        Patient patient2 = new Patient();
        homeAppointment.setPatients(List.of(patient2));

        List<Appointment> appointments = List.of(centerAppointment, homeAppointment);
        staff.setAppointments(appointments);

        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        // Mocks for mapper
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toPatientAppointment(any())).thenReturn(new PatientAppointmentResponse());
        when(appointmentMapper.toKitAppointmentResponse(any())).thenReturn(new KitAppointmentResponse());

        // Act
        AllAppointmentResponse response = appointmentService.getAppointmentByStaffId(authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getAllAppointmentAtCenterResponse().size());
        assertEquals(1, response.getAllAppointmentAtHomeResponse().size());

        verify(staffRepository).findById(1L);
        verify(appointmentMapper, atLeastOnce()).toShowAppointmentResponse(any());
        verify(appointmentMapper, atLeastOnce()).toServiceAppointmentResponse(any());
        verify(appointmentMapper, atLeastOnce()).toPatientAppointment(any());
        verify(appointmentMapper, atLeastOnce()).toKitAppointmentResponse(any());
    }

    @Test
    void getAppointmentBySlot_success() {
        long slotId = 1L;
        long staffId = 100L;

        // JWT Claims
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);

        // Setup Slot & Staff
        Slot slot = new Slot();
        slot.setSlotStatus(SlotStatus.BOOKED);
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setRole("SAMPLE_COLLECTOR");

        slot.setStaff(List.of(staff));
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setSlot(slot);

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PAID);
        appointment.setPayments(List.of(payment));

        // mock patients
        Patient patient = new Patient();
        appointment.setPatients(List.of(patient));

        Users user = new Users();
        appointment.setUsers(user);

        ServiceTest service = new ServiceTest();
        Kit kit = new Kit();
        service.setKit(kit);
        appointment.setServices(service);

        slot.setAppointment(List.of(appointment));
        Room room = new Room();
        slot.setRoom(room);

        // Mock data fetch
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        // Map responses
        when(appointmentMapper.toPatientAppointment(any())).thenReturn(new PatientAppointmentResponse());
        when(appointmentMapper.toKitAppointmentResponse(any())).thenReturn(new KitAppointmentResponse());
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toSlotAppointmentResponse(any())).thenReturn(new SlotAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toRoomAppointmentResponse(any())).thenReturn(new RoomAppointmentResponse());

        // Call the method
        List<AllAppointmentAtCenterResponse> result = appointmentService.getAppointmentBySlot(slotId, authentication);

        // Assertions
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getAllAppointments_success() {
        // Arrange
        long userId = 10L;
        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users user = new Users();
        user.setUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Appointment centerAppointment = new Appointment();
        centerAppointment.setAppointmentType(AppointmentType.CENTER);
        centerAppointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        centerAppointment.setUsers(user);
        centerAppointment.setSlot(new Slot()); // you may mock slot and room name if needed
        centerAppointment.setLocation(new Location());
        centerAppointment.setStaff(new Staff());
        centerAppointment.setServices(new ServiceTest());
        centerAppointment.setPayments(new ArrayList<>());

        Appointment homeAppointment = new Appointment();
        homeAppointment.setAppointmentType(AppointmentType.HOME);
        homeAppointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        homeAppointment.setUsers(user);
        ServiceTest homeService = new ServiceTest();
        homeService.setServiceType(ServiceType.CIVIL);
        homeService.setKit(new Kit());
        homeAppointment.setServices(homeService);
        homeAppointment.setStaff(new Staff());
        homeAppointment.setPayments(new ArrayList<>());

        when(appointmentRepository.findByUsers_UserId(userId))
                .thenReturn(new ArrayList<>(List.of(centerAppointment, homeAppointment))); // ✅ mutable


        // Mock mapping responses
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toSlotAppointmentResponse(any())).thenReturn(new SlotAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toLocationAppointmentResponse(any())).thenReturn(new LocationAppointmentResponse());
        when(appointmentMapper.toKitAppointmentResponse(any())).thenReturn(new KitAppointmentResponse());
        when(appointmentMapper.toPaymentAppointmentResponse(any())).thenReturn(new ArrayList<>());

        // Act
        AllAppointmentResponse response = appointmentService.getAllAppointments(authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getAllAppointmentAtCenterResponse().size());
        assertEquals(1, response.getAllAppointmentAtHomeResponse().size());

        verify(appointmentRepository).findByUsers_UserId(userId);
        verify(userRepository).findById(userId);
    }

    @Test
    void testGetAppointmentOfUser_success() {
        // Arrange
        Long staffId = 1L;
        Long userId = 2L;
        String phone = "0987654321";

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);

        Staff staff = new Staff();
        staff.setRole("CASHIER");
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        Users user = new Users();
        user.setPhone(phone);
        user.setUserId(userId);

        Appointment appointment = new Appointment();
        appointment.setAppointmentType(AppointmentType.CENTER);
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setUsers(user);
        appointment.setServices(new ServiceTest());

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PENDING);
        appointment.setPayments(List.of(payment));
        user.setAppointments(List.of(appointment));

        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        // Mocks for mappers
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toPriceAppointmentResponse(any())).thenReturn(List.of(new PriceAppointmentResponse()));
        when(appointmentMapper.toPaymentAppointmentResponse(any())).thenReturn(List.of(new PaymentAppointmentResponse()));

        UserPhoneRequest userPhoneRequest = new UserPhoneRequest();
        userPhoneRequest.setPhone(phone);

        // Act
        List<AllAppointmentAtCenterUserResponse> responses =
                appointmentService.getAppointmentOfUser(authentication, userPhoneRequest);

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        verify(userRepository).findByPhone(phone);
        verify(staffRepository).findById(staffId);
    }

    @Test
    void testUpdateAppointmentToGetSampleAgain_success() {
        // Arrange
        long appointmentId = 1L;
        Room room = new Room();
        Location location = new Location();
        room.setLocation(location);

        Slot newSlot = new Slot();
        newSlot.setSlotDate(LocalDate.now().plusDays(1));
        newSlot.setSlotStatus(SlotStatus.AVAILABLE);
        newSlot.setRoom(room);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setAppointmentDate(LocalDate.now());
        appointment.setSlot(new Slot());

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(slotRepository.findBySlotDateAndSlotStatus(any(), eq(SlotStatus.AVAILABLE)))
                .thenReturn(List.of(newSlot));

        // Act
        appointmentService.updateAppointmentToGetSampleAgain(appointmentId);

        // Assert
        assertEquals(AppointmentType.CENTER, appointment.getAppointmentType());
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getAppointmentStatus());
        assertEquals(location, appointment.getLocation());
        assertEquals(newSlot, appointment.getSlot());
        assertEquals(SlotStatus.BOOKED, newSlot.getSlotStatus());
    }

    @Test
    void testPayAppointment_setsTransitionDate() {
        long paymentId = 1L;
        long appointmentId = 2L;
        long serviceId = 3L;

        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setAmount(1000);
        Appointment appointment = new Appointment();
        ServiceTest service = new ServiceTest();
        service.setServiceId(serviceId);
        appointment.setServices(service);
        payment.setAppointment(appointment);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        doReturn(new CreatePaymentRequest()).when(appointmentService).createPayment(paymentId, serviceId);

        appointmentService.payAppointment(paymentId, appointmentId);

        assertEquals(LocalDate.now(), payment.getTransitionDate());
    }

    @Test
    void testPayAppointment_invokesCreatePaymentWithCorrectArguments() {
        long paymentId = 10L;
        long appointmentId = 20L;
        long serviceId = 30L;

        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setAmount(5000);
        Appointment appointment = new Appointment();
        ServiceTest service = new ServiceTest();
        service.setServiceId(serviceId);
        appointment.setServices(service);
        payment.setAppointment(appointment);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        doReturn(new CreatePaymentRequest()).when(appointmentService).createPayment(paymentId, serviceId);

        appointmentService.payAppointment(paymentId, appointmentId);

        verify(appointmentService, times(1)).createPayment(paymentId, serviceId);
    }

    @Test
    void testPayAppointment_updatesAppointmentNote() {
        long paymentId = 100L;
        long appointmentId = 200L;
        long serviceId = 300L;

        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.CASH);
        Appointment appointment = new Appointment();
        ServiceTest service = new ServiceTest();
        service.setServiceId(serviceId);
        appointment.setServices(service);
        payment.setAppointment(appointment);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        doReturn(new CreatePaymentRequest()).when(appointmentService).createPayment(paymentId, serviceId);

        appointmentService.payAppointment(paymentId, appointmentId);

        assertEquals("Đã thanh toán", appointment.getNote());
    }

    @Test
    void getAllAppointmentsResult_success() {
        // Arrange
        long appointmentId = 1L;
        long userId = 10L;

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users user = new Users();
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointment.setUsers(user);
        appointment.setPatients(List.of(new Patient()));
        appointment.setServices(new ServiceTest());
        appointment.setStaff(new Staff());
        appointment.setSampleList(List.of(new Sample()));

        Result result = new Result();
        result.setResultStatus(ResultStatus.COMPLETED);
        ResultDetail detail = new ResultDetail();
        ResultLocus locus = new ResultLocus();
        detail.setResultLoci(List.of(locus));
        result.setResultDetail(detail);
        appointment.setResults(List.of(result));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByUsers_UserId(userId)).thenReturn(List.of(appointment));

        // Mock mappers
        when(appointmentMapper.toPatientAppointmentService(anyList())).thenReturn(List.of(new PatientAppointmentResponse()));
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toSampleAppointmentResponse(anyList())).thenReturn(List.of(new SampleAppointmentResponse()));
        when(appointmentMapper.toResultAppointmentResponse(any())).thenReturn(new ResultAppointmentResponse());
        when(appointmentMapper.toResultDetailAppointmentResponse(any())).thenReturn(new ResultDetailAppointmentResponse());
        when(appointmentMapper.toResultLocusAppointmentResponse(any())).thenReturn(new ResultLocusAppointmentResponse());

        // Act
        List<AllAppointmentResult> results = appointmentService.getAllAppointmentsResult(authentication, appointmentId);

        // Assert
        assertEquals(1, results.size());
        assertNotNull(results.get(0).getResultAppointmentResponse());
    }

    @Test
    void getAllAppointmentsResult_shouldThrowException_whenResultNotCompleted() {
        // Arrange
        long appointmentId = 1L;
        long userId = 10L;

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users user = new Users();
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointment.setUsers(user);

        Result result = new Result();
        result.setResultStatus(ResultStatus.ERROR); // Not completed
        result.setResultDetail(new ResultDetail());
        appointment.setResults(List.of(result));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByUsers_UserId(userId)).thenReturn(List.of(appointment));

        // Act + Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            appointmentService.getAllAppointmentsResult(authentication, appointmentId);
        });

        assertEquals("Kết quả chưa có", exception.getMessage());
    }
    @Test
    void testPayAppointment_createPaymentFailure() {
        long paymentId = 11L;
        long appointmentId = 22L;
        long serviceId = 33L;

        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.CASH);
        Appointment appointment = new Appointment();
        ServiceTest service = new ServiceTest();
        service.setServiceId(serviceId);
        appointment.setServices(service);
        payment.setAppointment(appointment);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        doThrow(new RuntimeException("createPayment failed")).when(appointmentService).createPayment(paymentId, serviceId);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            appointmentService.payAppointment(paymentId, appointmentId);
        });

        assertEquals("Đã thanh toán", appointment.getNote());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        assertEquals("createPayment failed", ex.getMessage());
    }

    @Test
    void testPayAppointment_noUpdateOnPaymentRetrievalFailure() {
        long paymentId = 123L;
        long appointmentId = 456L;

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> {
            appointmentService.payAppointment(paymentId, appointmentId);
        });

        verify(appointmentRepository, never()).findById(anyLong());
        // No further interactions, so no updates to appointment or payment
    }

    @Test
    void testPayAppointment_noUpdateOnAppointmentRetrievalFailure() {
        long paymentId = 321L;
        long appointmentId = 654L;

        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.CASH);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> {
            appointmentService.payAppointment(paymentId, appointmentId);
        });

        // Payment should not be updated since appointment is not found
        assertNull(payment.getPaymentStatus());
        assertNull(payment.getTransitionDate());
    }

    @Test
    void testUpdateAppointmentStatus_onlyAppointmentStatusUpdated() {
        long slotId = 1L;
        long patientId = 2L;

        // Setup slot with one CONFIRMED appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setNote("original note");

        Slot slot = new Slot();
        slot.setAppointment(List.of(appointment));

        // Setup patient
        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.REGISTERED);

        // AppointmentRequest with new status, PatientRequest with null status
        AppointmentRequest appointmentRequest = new AppointmentRequest();
        appointmentRequest.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointmentRequest.setNote("new note");
        PatientRequest patientRequest = new PatientRequest();
        patientRequest.setPatientStatus(null);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        appointmentService.updateAppointmentStatus(slotId, patientId, appointmentRequest, patientRequest);

        assertEquals(AppointmentStatus.COMPLETED, appointment.getAppointmentStatus());
        assertEquals(PatientStatus.REGISTERED, patient.getPatientStatus());
        assertEquals("new note", appointment.getNote());
    }

    @Test
    void testUpdateAppointmentStatus_onlyPatientStatusUpdated() {
        long slotId = 1L;
        long patientId = 2L;

        // Setup slot with one CONFIRMED appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setNote("original note");

        Slot slot = new Slot();
        slot.setAppointment(List.of(appointment));

        // Setup patient
        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.REGISTERED);

        // AppointmentRequest with null status, PatientRequest with new status
        AppointmentRequest appointmentRequest = new AppointmentRequest();
        appointmentRequest.setAppointmentStatus(null);
        appointmentRequest.setNote("new note");
        PatientRequest patientRequest = new PatientRequest();
        patientRequest.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        appointmentService.updateAppointmentStatus(slotId, patientId, appointmentRequest, patientRequest);

        assertEquals(AppointmentStatus.CONFIRMED, appointment.getAppointmentStatus());
        assertEquals(PatientStatus.SAMPLE_COLLECTED, patient.getPatientStatus());
        assertEquals("new note", appointment.getNote());
    }

    @Test
    void testUpdateAppointmentStatus_noStatusProvided() {
        long slotId = 1L;
        long patientId = 2L;

        // Setup slot with one CONFIRMED appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setNote("original note");

        Slot slot = new Slot();
        slot.setAppointment(List.of(appointment));

        // Setup patient
        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.REGISTERED);

        // Both statuses null
        AppointmentRequest appointmentRequest = new AppointmentRequest();
        appointmentRequest.setAppointmentStatus(null);
        appointmentRequest.setNote("new note");
        PatientRequest patientRequest = new PatientRequest();
        patientRequest.setPatientStatus(null);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        appointmentService.updateAppointmentStatus(slotId, patientId, appointmentRequest, patientRequest);

        assertEquals(AppointmentStatus.CONFIRMED, appointment.getAppointmentStatus());
        assertEquals(PatientStatus.REGISTERED, patient.getPatientStatus());
        assertEquals("new note", appointment.getNote());
    }

    @Test
    void testUpdateAppointmentStatus_noConfirmedAppointments() {
        long slotId = 1L;
        long patientId = 2L;

        // Setup slot with one appointment NOT in CONFIRMED status
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.PENDING);
        appointment.setNote("original note");

        Slot slot = new Slot();
        slot.setAppointment(List.of(appointment));

        // Setup patient
        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.REGISTERED);

        AppointmentRequest appointmentRequest = new AppointmentRequest();
        appointmentRequest.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointmentRequest.setNote("new note");
        PatientRequest patientRequest = new PatientRequest();
        patientRequest.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        appointmentService.updateAppointmentStatus(slotId, patientId, appointmentRequest, patientRequest);

        assertEquals(AppointmentStatus.PENDING, appointment.getAppointmentStatus());
        assertEquals(PatientStatus.REGISTERED, patient.getPatientStatus());
        assertEquals("original note", appointment.getNote());
    }

    @Test
    void testUpdateAppointmentStatus_appointmentNoteNull() {
        long slotId = 1L;
        long patientId = 2L;

        // Setup slot with one CONFIRMED appointment with null note
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setNote(null);

        Slot slot = new Slot();
        slot.setAppointment(List.of(appointment));

        // Setup patient
        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.REGISTERED);

        AppointmentRequest appointmentRequest = new AppointmentRequest();
        appointmentRequest.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointmentRequest.setNote("should not update");
        PatientRequest patientRequest = new PatientRequest();
        patientRequest.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        appointmentService.updateAppointmentStatus(slotId, patientId, appointmentRequest, patientRequest);

        assertEquals(AppointmentStatus.COMPLETED, appointment.getAppointmentStatus());
        assertEquals(PatientStatus.SAMPLE_COLLECTED, patient.getPatientStatus());
        assertNull(appointment.getNote());
    }

    @Test
    void testUpdateAppointmentStatus_emptyAppointmentList() {
        long slotId = 1L;
        long patientId = 2L;

        // Setup slot with empty appointment list
        Slot slot = new Slot();
        slot.setAppointment(new ArrayList<>());

        // Setup patient
        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.REGISTERED);

        AppointmentRequest appointmentRequest = new AppointmentRequest();
        appointmentRequest.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointmentRequest.setNote("new note");
        PatientRequest patientRequest = new PatientRequest();
        patientRequest.setPatientStatus(PatientStatus.SAMPLE_COLLECTED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        // Should not throw any exception
        assertDoesNotThrow(() -> appointmentService.updateAppointmentStatus(slotId, patientId, appointmentRequest, patientRequest));
    }

    @Test
    void testCreatePaymentByWallet_success() {
        long paymentId = 1L, serviceId = 2L, appointmentId = 3L;
        long userId = 10L;

        // Mock JWT and authentication
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        // Mock Wallet with sufficient balance
        Wallet wallet = new Wallet();
        wallet.setBalance(10000L);

        // Mock Payment
        Payment payment = new Payment();
        payment.setAmount(5000.0);
        payment.setPaymentMethod(PaymentMethod.VN_PAY);

        // Mock Appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setAppointmentType(AppointmentType.CENTER);

        // Mock ServiceTest
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(serviceId);
        serviceTest.setServiceName("DNA Test");

        // Mock repository returns
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        // Act
        var result = appointmentService.createPaymentByWallet(paymentId, serviceId, appointmentId, authentication);

        // Assert
        assertNotNull(result);
        assertEquals(payment.getAmount(), result.getAmount());
        assertEquals("DNA Test", result.getOrderInfo());
        assertEquals(5000L, wallet.getBalance());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        assertEquals("Quý khách vui lòng kiểm tra kỹ ngày, giờ và thứ trong lịch hẹn để đến đúng giờ lấy mẫu. Xin cảm ơn!", appointment.getNote());
    }

    @Test
    void testCancelledAppointment_homeAppointment_success() {
        long appointmentId = 1L;

        // Mock Appointment of type HOME
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setAppointmentType(AppointmentType.HOME);

        // Patients
        Patient patient1 = new Patient();
        patient1.setPatientStatus(PatientStatus.REGISTERED);
        Patient patient2 = new Patient();
        patient2.setPatientStatus(PatientStatus.REGISTERED);
        appointment.setPatients(List.of(patient1, patient2));

        // KitDeliveryStatus
        KitDeliveryStatus kitDeliveryStatus = new KitDeliveryStatus();
        kitDeliveryStatus.setDeliveryStatus(DeliveryStatus.IN_PROGRESS);
        appointment.setKitDeliveryStatus(kitDeliveryStatus);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // Act
        appointmentService.cancelledAppointment(appointmentId);

        // Assert
        assertEquals(AppointmentStatus.CANCELLED, appointment.getAppointmentStatus());
        assertEquals(PatientStatus.CANCELLED, patient1.getPatientStatus());
        assertEquals(PatientStatus.CANCELLED, patient2.getPatientStatus());
        assertEquals("Đơn đăng ký đã được hủy", appointment.getNote());
        assertEquals(DeliveryStatus.FAILED, kitDeliveryStatus.getDeliveryStatus());
    }

    @Test
    void testBookAppointmentAtHome_missingUserAddress() {
        long serviceId = 1L;
        long priceId = 2L;

        Authentication auth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(10L);

        Users user = new Users();
        user.setUserId(10L);
        user.setAddress(null); // No address

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(new ServiceTest()));
        when(priceListRepository.findById(priceId)).thenReturn(Optional.of(new PriceList()));

        AppointmentRequest appointmentRequest = new AppointmentRequest();
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            appointmentService.bookAppointmentAtHome(
                appointmentRequest,
                auth,
                new ArrayList<>(),
                paymentRequest,
                serviceId,
                priceId
            );
        });

        assertEquals("Please update your address before booking.", ex.getMessage());
    }

    @Test
    void testCreatePaymentByWallet_insufficientBalance() {
        long paymentId = 1L, serviceId = 2L, appointmentId = 3L;
        long userId = 10L;

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        // Wallet with insufficient balance
        Wallet wallet = new Wallet();
        wallet.setBalance(100L);

        Payment payment = new Payment();
        payment.setAmount(5000.0);
        payment.setPaymentMethod(PaymentMethod.VN_PAY);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);

        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(serviceId);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            appointmentService.createPaymentByWallet(paymentId, serviceId, appointmentId, authentication);
        });

        assertEquals("Số dư trong tài khoản không đủ để thanh toán", ex.getMessage());
    }

    @Test
    void testConfirmAppointmentAtHome_kitQuantityZero() {
        long appointmentId = 1L;
        long userId = 10L;
        long serviceId = 20L;

        Users user = new Users();
        user.setUserId(userId);

        Kit kit = new Kit();
        kit.setQuantity(0); // No kits available
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setKit(kit);

        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setUsers(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            appointmentService.ConfirmAppointmentAtHome(appointmentId, userId, serviceId);
        });

        assertEquals("Cơ sở đã hết số lượng kit", ex.getMessage());
    }

    @Test
    void testGetAllAppointmentsResultForManager_multipleAppointments() {
        Long userId = 1L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users manager = new Users();
        manager.setUserId(userId);
        manager.setRoles(Set.of(Roles.MANAGER.name()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(manager));

        // Create two appointments, each with two results
        Appointment appointment1 = new Appointment();
        appointment1.setAppointmentStatus(AppointmentStatus.WAITING_MANAGER_APPROVAL);

        Result result1a = new Result();
        result1a.setResultStatus(ResultStatus.COMPLETED);
        ResultDetail detail1a = new ResultDetail();
        ResultLocus locus1a = new ResultLocus();
        detail1a.setResultLoci(List.of(locus1a));
        result1a.setResultDetail(detail1a);

        Result result1b = new Result();
        result1b.setResultStatus(ResultStatus.COMPLETED);
        ResultDetail detail1b = new ResultDetail();
        ResultLocus locus1b = new ResultLocus();
        detail1b.setResultLoci(List.of(locus1b));
        result1b.setResultDetail(detail1b);

        appointment1.setResults(List.of(result1a, result1b));

        Appointment appointment2 = new Appointment();
        appointment2.setAppointmentStatus(AppointmentStatus.WAITING_MANAGER_APPROVAL);

        Result result2a = new Result();
        result2a.setResultStatus(ResultStatus.COMPLETED);
        ResultDetail detail2a = new ResultDetail();
        ResultLocus locus2a = new ResultLocus();
        detail2a.setResultLoci(List.of(locus2a));
        result2a.setResultDetail(detail2a);

        appointment2.setResults(List.of(result2a));

        when(appointmentRepository.findAll()).thenReturn(List.of(appointment1, appointment2));

        // Mock all mappers for each result and appointment
        when(appointmentMapper.toPatientAppointmentService(any())).thenReturn(List.of());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toSampleAppointmentResponse(any())).thenReturn(List.of());
        when(appointmentMapper.toResultAppointmentResponse(any())).thenReturn(new ResultAppointmentResponse());
        when(appointmentMapper.toResultDetailAppointmentResponse(any())).thenReturn(new ResultDetailAppointmentResponse());
        when(appointmentMapper.toResultLocusAppointmentResponse(any())).thenReturn(new ResultLocusAppointmentResponse());

        List<AllAppointmentResult> results = appointmentService.getAllAppointmentsResultForManager(authentication, 1L);

        assertNotNull(results);
        assertEquals(2, results.size());
        // Each appointment should have its result list mapped
        for (AllAppointmentResult result : results) {
            assertNotNull(result.getResultAppointmentResponse());
        }
        verify(appointmentRepository).findAll();
        verify(userRepository).findById(userId);
    }


    @Test
    void getAllAppointmentsResultForManager_success() {
        // Mock JWT Authentication
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("id")).thenReturn(1L);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);

        // Mock User with MANAGER role
        Users user = new Users();
        user.setUserId(1L);
        user.setRoles(Set.of(Roles.MANAGER.name()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Mock Appointment with proper status
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.WAITING_MANAGER_APPROVAL);

        Result result = new Result();
        result.setResultStatus(ResultStatus.COMPLETED);

        ResultDetail resultDetail = new ResultDetail();
        ResultLocus locus = new ResultLocus();
        resultDetail.setResultLoci(List.of(locus));
        result.setResultDetail(resultDetail);

        appointment.setResults(List.of(result));
        appointment.setPatients(new ArrayList<>());
        appointment.setServices(new ServiceTest());
        appointment.setStaff(new Staff());
        appointment.setUsers(new Users());
        appointment.setSampleList(new ArrayList<>());

        when(appointmentRepository.findAll()).thenReturn(List.of(appointment));

        // Mock mapping
        when(appointmentMapper.toShowAppointmentResponse(appointment)).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(appointment.getStaff())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toServiceAppointmentResponse(appointment.getServices())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toPatientAppointmentService(appointment.getPatients())).thenReturn(new ArrayList<>());
        when(appointmentMapper.toUserAppointmentResponse(appointment.getUsers())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toSampleAppointmentResponse(appointment.getSampleList())).thenReturn(new ArrayList<>());
        when(appointmentMapper.toResultAppointmentResponse(result)).thenReturn(new ResultAppointmentResponse());
        when(appointmentMapper.toResultDetailAppointmentResponse(resultDetail)).thenReturn(new ResultDetailAppointmentResponse());
        when(appointmentMapper.toResultLocusAppointmentResponse(locus)).thenReturn(new ResultLocusAppointmentResponse());

        // Act
        List<AllAppointmentResult> results = appointmentService.getAllAppointmentsResultForManager(authentication, 1L);

        // Assert
        assertEquals(1, results.size());
        AllAppointmentResult resultData = results.get(0);
        assertNotNull(resultData.getResultAppointmentResponse());
        assertNotNull(resultData.getResultDetailAppointmentResponse());
        assertNotNull(resultData.getResultLocusAppointmentResponse());
    }

    @Test
    void getAllAppointmentsResultForManager_throwIfNotManager() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("id")).thenReturn(2L);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);

        Users user = new Users();
        user.setUserId(2L);
        user.setRoles(Set.of(Roles.STAFF.name()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            appointmentService.getAllAppointmentsResultForManager(authentication, 1L);
        });

        assertEquals("Chỉ Có Manager mới có quyền xem", exception.getMessage());
    }

    @Test
    void getAllAppointmentsResultForManager_throwIfResultNotCompleted() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("id")).thenReturn(3L);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);

        Users user = new Users();
        user.setUserId(3L);
        user.setRoles(Set.of(Roles.MANAGER.name()));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.WAITING_MANAGER_APPROVAL);

        Result result = new Result();
        result.setResultStatus(ResultStatus.PENDING); // Not COMPLETED

        appointment.setResults(List.of(result));
        when(appointmentRepository.findAll()).thenReturn(List.of(appointment));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            appointmentService.getAllAppointmentsResultForManager(authentication, 1L);
        });

        assertEquals("Kết quả chưa có", exception.getMessage());
    }

    @Test
    void testGetAllAppointmentsResultForManager_noAppointments() {
        Long userId = 3L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users manager = new Users();
        manager.setUserId(userId);
        manager.setRoles(Set.of(Roles.MANAGER.name()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(manager));

        when(appointmentRepository.findAll()).thenReturn(Collections.emptyList());

        List<AllAppointmentResult> results = appointmentService.getAllAppointmentsResultForManager(authentication, 1L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(appointmentRepository).findAll();
        verify(userRepository).findById(userId);
    }

    @Test
    void testGetAllAppointmentsResultForManager_userNotFound() {
        Long userId = 4L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> {
            appointmentService.getAllAppointmentsResultForManager(authentication, 1L);
        });
        assertEquals(ErrorCodeUser.USER_NOT_EXISTED, ex.getErrorCode());
        verify(userRepository).findById(userId);
        verify(appointmentRepository, never()).findAll();
    }

    @Test
    void testGetAllAppointmentsResultForManager_userNotManager() {
        Long userId = 5L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users user = new Users();
        user.setUserId(userId);
        user.setRoles(Set.of(Roles.STAFF.name())); // Not MANAGER
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            appointmentService.getAllAppointmentsResultForManager(authentication, 1L);
        });
        assertEquals("Chỉ Có Manager mới có quyền xem", ex.getMessage());
        verify(userRepository).findById(userId);
        verify(appointmentRepository, never()).findAll();
    }

    @Test
    void testGetAllAppointmentsResultForManager_resultNotCompleted() {
        Long userId = 6L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users manager = new Users();
        manager.setUserId(userId);
        manager.setRoles(Set.of(Roles.MANAGER.name()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(manager));

        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.WAITING_MANAGER_APPROVAL);

        Result result = new Result();
        result.setResultStatus(ResultStatus.ERROR); // Not COMPLETED
        ResultDetail detail = new ResultDetail();
        ResultLocus locus = new ResultLocus();
        detail.setResultLoci(List.of(locus));
        result.setResultDetail(detail);
        appointment.setResults(List.of(result));

        when(appointmentRepository.findAll()).thenReturn(List.of(appointment));

        when(appointmentMapper.toPatientAppointmentService(any())).thenReturn(List.of());
        when(appointmentMapper.toServiceAppointmentResponse(any())).thenReturn(new ServiceAppointmentResponse());
        when(appointmentMapper.toShowAppointmentResponse(any())).thenReturn(new ShowAppointmentResponse());
        when(appointmentMapper.toStaffAppointmentResponse(any())).thenReturn(new StaffAppointmentResponse());
        when(appointmentMapper.toUserAppointmentResponse(any())).thenReturn(new UserAppointmentResponse());
        when(appointmentMapper.toSampleAppointmentResponse(any())).thenReturn(List.of());
        when(appointmentMapper.toResultAppointmentResponse(any())).thenReturn(new ResultAppointmentResponse());
        when(appointmentMapper.toResultDetailAppointmentResponse(any())).thenReturn(new ResultDetailAppointmentResponse());
        when(appointmentMapper.toResultLocusAppointmentResponse(any())).thenReturn(new ResultLocusAppointmentResponse());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            appointmentService.getAllAppointmentsResultForManager(authentication, 1L);
        });
        assertEquals("Kết quả chưa có", ex.getMessage());
        verify(appointmentRepository).findAll();
        verify(userRepository).findById(userId);
    }

    @Test
    void testGetAppointmentToViewResult_returnsWaitingManagerApprovalAppointments() {
        // Arrange
        AppointmentInfoForManagerDTO dto1 = new AppointmentInfoForManagerDTO(1L, LocalDate.of(2024, 6, 1), AppointmentStatus.WAITING_MANAGER_APPROVAL);
        AppointmentInfoForManagerDTO dto2 = new AppointmentInfoForManagerDTO(2L, LocalDate.of(2024, 6, 2), AppointmentStatus.WAITING_MANAGER_APPROVAL);
        List<AppointmentInfoForManagerDTO> expectedList = Arrays.asList(dto1, dto2);

        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.WAITING_MANAGER_APPROVAL))).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedList);

        // Act
        List<AppointmentInfoForManagerDTO> result = appointmentService.getAppointmentToViewResult();

        // Assert
        assertEquals(2, result.size());
        assertEquals(dto1, result.get(0));
        assertEquals(dto2, result.get(1));
        verify(entityManager).createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class));
        verify(query).setParameter("status", AppointmentStatus.WAITING_MANAGER_APPROVAL);
        verify(query).getResultList();
    }

    @Test
    void testGetAppointmentToViewResult_returnsEmptyListWhenNoMatchingAppointments() {
        // Arrange
        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.WAITING_MANAGER_APPROVAL))).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        // Act
        List<AppointmentInfoForManagerDTO> result = appointmentService.getAppointmentToViewResult();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(entityManager).createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class));
        verify(query).setParameter("status", AppointmentStatus.WAITING_MANAGER_APPROVAL);
        verify(query).getResultList();
    }

    @Test
    void testGetAppointmentToViewResult_correctDTOFieldMapping() {
        // Arrange
        long expectedId = 123L;
        LocalDate expectedDate = LocalDate.of(2024, 6, 10);
        AppointmentStatus expectedStatus = AppointmentStatus.WAITING_MANAGER_APPROVAL;
        AppointmentInfoForManagerDTO dto = new AppointmentInfoForManagerDTO(expectedId, expectedDate, expectedStatus);

        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.WAITING_MANAGER_APPROVAL))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(dto));

        // Act
        List<AppointmentInfoForManagerDTO> result = appointmentService.getAppointmentToViewResult();

        // Assert
        assertEquals(1, result.size());
        AppointmentInfoForManagerDTO actual = result.get(0);
        assertEquals(expectedId, actual.getAppointmentId());
        assertEquals(expectedDate, actual.getAppointmentDate());
        assertEquals(expectedStatus, actual.getAppointmentStatus());
    }

    @Test
    void testGetAppointmentToViewResult_databaseErrorThrowsException() {
        // Arrange
        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.WAITING_MANAGER_APPROVAL))).thenReturn(query);
        when(query.getResultList()).thenThrow(new RuntimeException("DB error"));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> appointmentService.getAppointmentToViewResult());
        assertEquals("DB error", ex.getMessage());
    }



    @Test
    void testGetAppointmentToViewResult_dtoConstructionFailureThrowsException() {
        // Arrange
        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.WAITING_MANAGER_APPROVAL))).thenReturn(query);
        when(query.getResultList()).thenThrow(new IllegalArgumentException("DTO construction failed"));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> appointmentService.getAppointmentToViewResult());
        assertEquals("DTO construction failed", ex.getMessage());
    }

    @Test
    void testGetAppointmentToRefund_returnsRefundAppointments() {
        AppointmentInfoForManagerDTO dto1 = new AppointmentInfoForManagerDTO(1L, LocalDate.of(2024, 6, 1), AppointmentStatus.REFUND);
        AppointmentInfoForManagerDTO dto2 = new AppointmentInfoForManagerDTO(2L, LocalDate.of(2024, 6, 2), AppointmentStatus.REFUND);
        List<AppointmentInfoForManagerDTO> expectedList = Arrays.asList(dto1, dto2);

        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.REFUND))).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedList);

        List<AppointmentInfoForManagerDTO> result = appointmentService.getAppointmentToRefund();

        assertEquals(2, result.size());
        assertEquals(dto1, result.get(0));
        assertEquals(dto2, result.get(1));
        verify(entityManager).createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class));
        verify(query).setParameter("status", AppointmentStatus.REFUND);
        verify(query).getResultList();
    }


    @Test
    void patientCheckIn_shouldUpdateAppointmentAndPatientAndRefundPayments() {
        // Given
        long patientId = 1L;
        long appointmentId = 2L;

        Patient patient = new Patient();
        patient.setPatientStatus(PatientStatus.REGISTERED);

        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setNote("");

        Payment payment1 = new Payment();
        payment1.setPaymentStatus(PaymentStatus.PAID);

        Payment payment2 = new Payment();
        payment2.setPaymentStatus(PaymentStatus.PAID);

        appointment.setPayments(List.of(payment1, payment2));

        // Mock behavior
        Mockito.when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        Mockito.when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        // When
        appointmentService.patientCheckIn(patientId, appointmentId);

        // Then
        Assertions.assertEquals("Bệnh nhân vắng mặt nên không thể hoàn thành việc lấy mẫu", appointment.getNote());
        Assertions.assertEquals(AppointmentStatus.CANCELLED, appointment.getAppointmentStatus());
        Assertions.assertEquals(PatientStatus.NO_SHOW, patient.getPatientStatus());
        Assertions.assertTrue(appointment.getPayments().stream()
                .allMatch(p -> p.getPaymentStatus() == PaymentStatus.REFUNDED));
    }

    @Test
    void patientCheckIn_shouldThrowException_whenPatientNotFound() {
        // Given
        long patientId = 1L;
        long appointmentId = 2L;

        Mockito.when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        // Then
        AppException exception = Assertions.assertThrows(AppException.class, () ->
                appointmentService.patientCheckIn(patientId, appointmentId));

        Assertions.assertEquals(ErrorCodeUser.PATIENT_INFO_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void patientCheckIn_shouldThrowException_whenAppointmentNotFound() {
        // Given
        long patientId = 1L;
        long appointmentId = 2L;

        Mockito.when(patientRepository.findById(patientId)).thenReturn(Optional.of(new Patient()));
        Mockito.when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        // Then
        AppException exception = Assertions.assertThrows(AppException.class, () ->
                appointmentService.patientCheckIn(patientId, appointmentId));

        Assertions.assertEquals(ErrorCodeUser.APPOINTMENT_NOT_EXISTS, exception.getErrorCode());
    }


    @Test
    void testGetAppointmentToRefund_returnsEmptyListWhenNoRefundAppointments() {
        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.REFUND))).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        List<AppointmentInfoForManagerDTO> result = appointmentService.getAppointmentToRefund();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(entityManager).createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class));
        verify(query).setParameter("status", AppointmentStatus.REFUND);
        verify(query).getResultList();
    }

    @Test
    void testGetAppointmentToRefund_correctDTOFieldMapping() {
        long expectedId = 123L;
        LocalDate expectedDate = LocalDate.of(2024, 6, 10);
        AppointmentStatus expectedStatus = AppointmentStatus.REFUND;
        AppointmentInfoForManagerDTO dto = new AppointmentInfoForManagerDTO(expectedId, expectedDate, expectedStatus);

        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.REFUND))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(dto));

        List<AppointmentInfoForManagerDTO> result = appointmentService.getAppointmentToRefund();

        assertEquals(1, result.size());
        AppointmentInfoForManagerDTO actual = result.get(0);
        assertEquals(expectedId, actual.getAppointmentId());
        assertEquals(expectedDate, actual.getAppointmentDate());
        assertEquals(expectedStatus, actual.getAppointmentStatus());
    }

    @Test
    void testGetAppointmentToRefund_databaseErrorThrowsException() {
        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.REFUND))).thenReturn(query);
        when(query.getResultList()).thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> appointmentService.getAppointmentToRefund());
        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void testGetAppointmentToRefund_dtoConstructionFailureThrowsException() {
        when(entityManager.createQuery(anyString(), eq(AppointmentInfoForManagerDTO.class))).thenReturn(query);
        when(query.setParameter(eq("status"), eq(AppointmentStatus.REFUND))).thenReturn(query);
        when(query.getResultList()).thenThrow(new IllegalArgumentException("DTO construction failed"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> appointmentService.getAppointmentToRefund());
        assertEquals("DTO construction failed", ex.getMessage());
    }





}

