package swp.project.adn_backend;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import swp.project.adn_backend.dto.InfoDTO.OrderInfoResponseDTO;
import swp.project.adn_backend.dto.InfoDTO.PaymentInfoDTO;
import swp.project.adn_backend.dto.request.payment.PaymentRequest;
import swp.project.adn_backend.entity.Appointment;
import swp.project.adn_backend.entity.Payment;
import swp.project.adn_backend.entity.Users;
import swp.project.adn_backend.entity.Wallet;
import swp.project.adn_backend.entity.WalletTransaction;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.PaymentStatus;
import swp.project.adn_backend.enums.PaymentMethod;
import swp.project.adn_backend.enums.TransactionStatus;
import swp.project.adn_backend.enums.TransactionType;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.PaymentMapper;
import swp.project.adn_backend.repository.AppointmentRepository;
import swp.project.adn_backend.repository.PaymentRepository;
import swp.project.adn_backend.repository.UserRepository;
import swp.project.adn_backend.service.payment.PaymentService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import swp.project.adn_backend.service.registerServiceTestService.AppointmentService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private EntityManager entityManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private Authentication authentication;
    @Mock
    private Jwt jwt;

    @Mock
    private TypedQuery<PaymentInfoDTO> paymentInfoTypedQuery;
    @Mock
    private TypedQuery<OrderInfoResponseDTO> orderInfoTypedQuery;
    @InjectMocks
    private AppointmentService appointmentService;
    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, paymentMapper, entityManager, userRepository, appointmentRepository);
    }

    @Test
    void testCreatePaymentWithValidRequest() {
        PaymentRequest paymentRequest = mock(PaymentRequest.class);
        Payment mappedPayment = mock(Payment.class);
        Payment savedPayment = mock(Payment.class);

        when(paymentMapper.toPayment(paymentRequest)).thenReturn(mappedPayment);
        when(paymentRepository.save(mappedPayment)).thenReturn(savedPayment);

        Payment result = paymentService.createPayment(paymentRequest);

        assertEquals(savedPayment, result);
        verify(paymentMapper).toPayment(paymentRequest);
        verify(paymentRepository).save(mappedPayment);
    }

    @Test
    void testGetAllPaymentForAuthenticatedUser() {
        Long userId = 123L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users user = mock(Users.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String expectedJpql = "SELECT new swp.project.adn_backend.dto.InfoDTO.PaymentInfoDTO(" +
                "s.paymentId, s.amount, s.paymentMethod, s.getPaymentStatus) " +
                "FROM Payment s Where s.Users.userId=:userId";
        when(entityManager.createQuery(expectedJpql, PaymentInfoDTO.class)).thenReturn(paymentInfoTypedQuery);
        when(paymentInfoTypedQuery.setParameter("userId", userId)).thenReturn(paymentInfoTypedQuery);

        List<PaymentInfoDTO> paymentInfoList = Arrays.asList(
                mock(PaymentInfoDTO.class),
                mock(PaymentInfoDTO.class)
        );
        when(paymentInfoTypedQuery.getResultList()).thenReturn(paymentInfoList);

        List<PaymentInfoDTO> result = paymentService.getAllPayment(authentication);

        assertEquals(paymentInfoList, result);
        verify(userRepository).findById(userId);
        verify(entityManager).createQuery(expectedJpql, PaymentInfoDTO.class);
        verify(paymentInfoTypedQuery).setParameter("userId", userId);
        verify(paymentInfoTypedQuery).getResultList();
    }

    @Test
    void testGetOrderPaymentInfoWithExistingPayments() {
        String expectedJpql = "SELECT new swp.project.adn_backend.dto.InfoDTO.OrderInfoResponseDTO(" +
                "s.paymentId, s.amount, s.paymentMethod, s.getPaymentStatus, s.transitionDate) " +
                "FROM Payment s";
        when(entityManager.createQuery(expectedJpql, OrderInfoResponseDTO.class)).thenReturn(orderInfoTypedQuery);

        List<OrderInfoResponseDTO> orderInfoList = Arrays.asList(
                mock(OrderInfoResponseDTO.class),
                mock(OrderInfoResponseDTO.class)
        );
        when(orderInfoTypedQuery.getResultList()).thenReturn(orderInfoList);

        List<OrderInfoResponseDTO> result = paymentService.getOrderPaymentInfo();

        assertEquals(orderInfoList, result);
        verify(entityManager).createQuery(expectedJpql, OrderInfoResponseDTO.class);
        verify(orderInfoTypedQuery).getResultList();
    }

    @Test
    void testGetAllPaymentWithNonExistentUser() {
        Long userId = 456L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> paymentService.getAllPayment(authentication));
        assertEquals(ErrorCodeUser.USER_NOT_EXISTED, exception.getErrorCode());
        verify(userRepository).findById(userId);
        verify(entityManager, never()).createQuery(anyString(), eq(PaymentInfoDTO.class));
    }

    @Test
    void testUpdatePaymentMethodWithNonExistentPayment() {
        long paymentId = 789L;
        PaymentRequest paymentRequest = mock(PaymentRequest.class);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> paymentService.updatePaymentMethod(paymentId, paymentRequest));
        assertEquals(ErrorCodeUser.PAYMENT_INFO_NOT_EXISTS, exception.getErrorCode());
        verify(paymentRepository).findById(paymentId);
    }

    @Test
    void testGetAllPaymentForUserWithNoPayments() {
        Long userId = 321L;
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);

        Users user = mock(Users.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String expectedJpql = "SELECT new swp.project.adn_backend.dto.InfoDTO.PaymentInfoDTO(" +
                "s.paymentId, s.amount, s.paymentMethod, s.getPaymentStatus) " +
                "FROM Payment s Where s.Users.userId=:userId";
        when(entityManager.createQuery(expectedJpql, PaymentInfoDTO.class)).thenReturn(paymentInfoTypedQuery);
        when(paymentInfoTypedQuery.setParameter("userId", userId)).thenReturn(paymentInfoTypedQuery);

        when(paymentInfoTypedQuery.getResultList()).thenReturn(Collections.emptyList());

        List<PaymentInfoDTO> result = paymentService.getAllPayment(authentication);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository).findById(userId);
        verify(entityManager).createQuery(expectedJpql, PaymentInfoDTO.class);
        verify(paymentInfoTypedQuery).setParameter("userId", userId);
        verify(paymentInfoTypedQuery).getResultList();
    }

    @Test
    void testWalletRefundWithPaidPayments() {
        long appointmentId = 1L;
        Appointment appointment = mock(Appointment.class);
        Users user = mock(Users.class);
        Wallet wallet = mock(Wallet.class);

        Payment paidPayment = mock(Payment.class);
        when(paidPayment.getGetPaymentStatus()).thenReturn(PaymentStatus.PAID);
        when(paidPayment.getAmount()).thenReturn(1000.0);

        List<Payment> payments = Collections.singletonList(paidPayment);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointment.getUsers()).thenReturn(user);
        when(appointment.getPayments()).thenReturn(payments);
        when(user.getWallet()).thenReturn(wallet);
        when(wallet.getBalance()).thenReturn(5000L);

        paymentService.walletRefund(appointmentId);

        verify(paidPayment).setPaymentStatus(PaymentStatus.REFUNDED);
        verify(wallet).setBalance(6000L);
        verify(user, atLeastOnce()).getWallet();
    }

    @Test
    void testUpdatePaymentMethodForExistingPayment() {
        long paymentId = 2L;
        PaymentRequest paymentRequest = mock(PaymentRequest.class);
        Payment payment = mock(Payment.class);
        PaymentMethod newMethod = PaymentMethod.valueOf("CASH"); // or any valid PaymentMethod

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRequest.getPaymentMethod()).thenReturn(newMethod);

        paymentService.updatePaymentMethod(paymentId, paymentRequest);

        verify(payment).setPaymentMethod(newMethod);
    }

    @Test
    void testGetOrderPaymentInfoWithNoPayments() {
        String expectedJpql = "SELECT new swp.project.adn_backend.dto.InfoDTO.OrderInfoResponseDTO(" +
                "s.paymentId, s.amount, s.paymentMethod, s.getPaymentStatus, s.transitionDate) " +
                "FROM Payment s";
        @SuppressWarnings("unchecked")
        jakarta.persistence.TypedQuery<swp.project.adn_backend.dto.InfoDTO.OrderInfoResponseDTO> query = mock(jakarta.persistence.TypedQuery.class);

        when(entityManager.createQuery(expectedJpql, swp.project.adn_backend.dto.InfoDTO.OrderInfoResponseDTO.class)).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        List<swp.project.adn_backend.dto.InfoDTO.OrderInfoResponseDTO> result = paymentService.getOrderPaymentInfo();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(entityManager).createQuery(expectedJpql, swp.project.adn_backend.dto.InfoDTO.OrderInfoResponseDTO.class);
        verify(query).getResultList();
    }

    @Test
    void testWalletRefundWithNonExistentAppointment() {
        long appointmentId = 999L;
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> paymentService.walletRefund(appointmentId));
        assertEquals(ErrorCodeUser.APPOINTMENT_NOT_EXISTS, exception.getErrorCode());
        verify(appointmentRepository).findById(appointmentId);
    }

    @Test
    void testWalletRefundWithNoPaidPayments() {
        long appointmentId = 3L;
        Appointment appointment = mock(Appointment.class);
        Users user = mock(Users.class);
        Wallet wallet = mock(Wallet.class);

        Payment unpaidPayment = mock(Payment.class);
        when(unpaidPayment.getGetPaymentStatus()).thenReturn(PaymentStatus.PENDING);

        List<Payment> payments = Collections.singletonList(unpaidPayment);

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointment.getUsers()).thenReturn(user);
        when(appointment.getPayments()).thenReturn(payments);
        lenient().when(user.getWallet()).thenReturn(wallet); // ✅ FIXED

        paymentService.walletRefund(appointmentId);

        verify(unpaidPayment, never()).setPaymentStatus(PaymentStatus.REFUNDED);
        verify(wallet, never()).setBalance(anyLong());
    }


    @Test
    void testCreatePaymentWithInvalidRequest() {
        PaymentRequest invalidRequest = mock(PaymentRequest.class);
        when(paymentMapper.toPayment(invalidRequest)).thenThrow(new AppException(ErrorCodeUser.INVALID_REQUEST));

        AppException exception = assertThrows(AppException.class, () -> paymentService.createPayment(invalidRequest));
        assertEquals(ErrorCodeUser.INVALID_REQUEST, exception.getErrorCode());
        verify(paymentMapper).toPayment(invalidRequest);
        verify(paymentRepository, never()).save(any());
    }
   
}
