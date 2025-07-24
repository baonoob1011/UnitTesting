package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import swp.project.adn_backend.dto.InfoDTO.KitDeliveryStatusInfoByAppointmentDTO;
import swp.project.adn_backend.dto.InfoDTO.KitDeliveryStatusInfoDTO;
import swp.project.adn_backend.dto.InfoDTO.KitDeliveryStatusInfoStaffDTO;
import swp.project.adn_backend.dto.request.Kit.KitDeliveryStatusRequest;
import swp.project.adn_backend.dto.response.kit.KitDeliveryStatusResponse;
import swp.project.adn_backend.entity.Appointment;
import swp.project.adn_backend.entity.KitDeliveryStatus;
import swp.project.adn_backend.entity.Staff;
import swp.project.adn_backend.entity.Users;
import swp.project.adn_backend.enums.DeliveryStatus;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.KitDeliveryStatusMapper;
import swp.project.adn_backend.repository.AppointmentRepository;
import swp.project.adn_backend.repository.KitDeliveryStatusRepository;
import swp.project.adn_backend.repository.StaffRepository;
import swp.project.adn_backend.repository.UserRepository;
import swp.project.adn_backend.service.Kit.KitDeliveryStatusService;
import swp.project.adn_backend.service.slot.StaffAssignmentTracker;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KitDeliveryStatusServiceTest {

    @Mock
    private KitDeliveryStatusRepository kitDeliveryStatusRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private KitDeliveryStatusMapper kitDeliveryStatusMapper;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private StaffAssignmentTracker staffAssignmentTracker;
    @Mock
    private Authentication authentication;
    @Mock
    private Jwt jwt;
    @Mock
    private TypedQuery<KitDeliveryStatusInfoDTO> kitDeliveryStatusInfoDTOQuery;
    @Mock
    private TypedQuery<KitDeliveryStatusInfoStaffDTO> kitDeliveryStatusInfoStaffDTOQuery;
    @Mock
    private TypedQuery<KitDeliveryStatusInfoByAppointmentDTO> kitDeliveryStatusInfoByAppointmentDTOQuery;

    private KitDeliveryStatusService kitDeliveryStatusService;

    @BeforeEach
    void setUp() {
        kitDeliveryStatusService = new KitDeliveryStatusService(
                kitDeliveryStatusRepository,
                userRepository,
                entityManager,
                appointmentRepository,
                kitDeliveryStatusMapper,
                staffRepository,
                staffAssignmentTracker
        );
    }

    @Test
    void testGetKitDeliveryStatusWithValidUser() {
        Long userId = 123L;
        Users user = new Users();
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String expectedJpql = "SELECT new swp.project.adn_backend.dto.InfoDTO.KitDeliveryStatusInfoDTO(" +
                "s.kitDeliveryStatusId, s.deliveryStatus, s.createOrderDate, s.returnDate) " +
                "FROM KitDeliveryStatus s WHERE s.users.userId = :userId " +
                "And s.deliveryStatus <> :excludedStatus";
        when(entityManager.createQuery(expectedJpql, KitDeliveryStatusInfoDTO.class)).thenReturn(kitDeliveryStatusInfoDTOQuery);
        when(kitDeliveryStatusInfoDTOQuery.setParameter("userId", userId)).thenReturn(kitDeliveryStatusInfoDTOQuery);
        when(kitDeliveryStatusInfoDTOQuery.setParameter("excludedStatus", DeliveryStatus.COMPLETED)).thenReturn(kitDeliveryStatusInfoDTOQuery);

        List<KitDeliveryStatusInfoDTO> expectedList = List.of(
                new KitDeliveryStatusInfoDTO(1L, DeliveryStatus.PENDING, LocalDate.now(), null)
        );
        when(kitDeliveryStatusInfoDTOQuery.getResultList()).thenReturn(expectedList);

        List<KitDeliveryStatusInfoDTO> result = kitDeliveryStatusService.getKitDeliveryStatus(authentication);

        assertEquals(expectedList, result);
        verify(userRepository).findById(userId);
        verify(entityManager).createQuery(anyString(), eq(KitDeliveryStatusInfoDTO.class));
        verify(kitDeliveryStatusInfoDTOQuery).setParameter("userId", userId);
        verify(kitDeliveryStatusInfoDTOQuery).setParameter("excludedStatus", DeliveryStatus.COMPLETED);
        verify(kitDeliveryStatusInfoDTOQuery).getResultList();
    }

    @Test
    void testGetKitDeliveryStatusStaffWithValidRole() {
        Long staffId = 456L;
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setRole("STAFF_AT_HOME");

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        String expectedJpql = "SELECT new swp.project.adn_backend.dto.InfoDTO.KitDeliveryStatusInfoStaffDTO(" +
                "s.kitDeliveryStatusId, s.deliveryStatus, s.createOrderDate, s.returnDate, s.appointment.appointmentId, s.appointment.appointmentType, s.appointment.appointmentStatus) " +
                "FROM KitDeliveryStatus s WHERE s.staff.staffId = :staffId";
        when(entityManager.createQuery(expectedJpql, KitDeliveryStatusInfoStaffDTO.class)).thenReturn(kitDeliveryStatusInfoStaffDTOQuery);
        when(kitDeliveryStatusInfoStaffDTOQuery.setParameter("staffId", staffId)).thenReturn(kitDeliveryStatusInfoStaffDTOQuery);

        List<KitDeliveryStatusInfoStaffDTO> expectedList = List.of(
                new KitDeliveryStatusInfoStaffDTO(2L, DeliveryStatus.DELIVERED, LocalDate.now(), null, 10L, null, null)
        );
        when(kitDeliveryStatusInfoStaffDTOQuery.getResultList()).thenReturn(expectedList);

        List<KitDeliveryStatusInfoStaffDTO> result = kitDeliveryStatusService.getKitDeliveryStatusStaff(authentication);

        assertEquals(expectedList, result);
        verify(staffRepository).findById(staffId);
        verify(entityManager).createQuery(anyString(), eq(KitDeliveryStatusInfoStaffDTO.class));
        verify(kitDeliveryStatusInfoStaffDTOQuery).setParameter("staffId", staffId);
        verify(kitDeliveryStatusInfoStaffDTOQuery).getResultList();
    }

    @Test
    void testUpdateKitDeliveryStatusToDone() {
        long appointmentId = 789L;
        Appointment appointment = mock(Appointment.class);
        KitDeliveryStatus kitDeliveryStatus = mock(KitDeliveryStatus.class);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointment.getKitDeliveryStatus()).thenReturn(kitDeliveryStatus);
        when(kitDeliveryStatus.getDeliveryStatus()).thenReturn(DeliveryStatus.DELIVERED);

        KitDeliveryStatusRequest request = new KitDeliveryStatusRequest();
        request.setDeliveryStatus(DeliveryStatus.DONE);

        KitDeliveryStatusResponse expectedResponse = new KitDeliveryStatusResponse();
        when(kitDeliveryStatusMapper.toKitDeliveryStatusResponse(kitDeliveryStatus)).thenReturn(expectedResponse);

        KitDeliveryStatusResponse response = kitDeliveryStatusService.updateKitDeliveryStatus(request, appointmentId);

        verify(kitDeliveryStatus).setReturnDate(any(LocalDate.class));
        verify(appointment).setNote("Đã nhận lại bộ kit");
        verify(kitDeliveryStatus).setDeliveryStatus(DeliveryStatus.DONE);
        assertEquals(expectedResponse, response);
    }

    @Test
    void testGetKitDeliveryStatusWithNonExistentUser() {
        Long userId = 321L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> kitDeliveryStatusService.getKitDeliveryStatus(authentication));
        assertEquals(ErrorCodeUser.USER_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void testGetKitDeliveryStatusStaffWithInvalidRole() {
        Long staffId = 654L;
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setRole("STAFF_AT_LAB");

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(staffId);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> kitDeliveryStatusService.getKitDeliveryStatusStaff(authentication));
        assertEquals("Chỉ có nhân viên thu mẫu tại nhà mới có thể lấy", ex.getMessage());
    }

    @Test
    void testUpdateKitDeliveryStatusToPreviousState() {
        long appointmentId = 999L;
        Appointment appointment = mock(Appointment.class);
        KitDeliveryStatus kitDeliveryStatus = mock(KitDeliveryStatus.class);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointment.getKitDeliveryStatus()).thenReturn(kitDeliveryStatus);
        when(kitDeliveryStatus.getDeliveryStatus()).thenReturn(DeliveryStatus.DELIVERED);

        KitDeliveryStatusRequest request = new KitDeliveryStatusRequest();
        request.setDeliveryStatus(DeliveryStatus.PENDING); // previous state

        RuntimeException ex = assertThrows(RuntimeException.class, () -> kitDeliveryStatusService.updateKitDeliveryStatus(request, appointmentId));
        assertEquals("Không thể cập nhật trạng thái lùi về bước trước đó.", ex.getMessage());
    }
}