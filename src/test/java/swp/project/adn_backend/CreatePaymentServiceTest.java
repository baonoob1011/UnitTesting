package swp.project.adn_backend.service.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import swp.project.adn_backend.dto.request.payment.CreatePaymentRequest;
import swp.project.adn_backend.dto.request.payment.WalletRequest;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.*;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.repository.*;
import swp.project.adn_backend.dto.InfoDTO.WalletTransitionDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import swp.project.adn_backend.service.registerServiceTestService.AppointmentService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    ServiceTestRepository serviceTestRepository;
    @Mock
    InvoiceRepository invoiceRepository;
    @Mock
    WalletRepository walletRepository;
    @Mock
    AppointmentService appointmentService;
    @Mock
    UserRepository userRepository;
    @Mock
    WalletTransactionRepository walletTransactionRepository;
    @Mock
    EntityManager entityManager;
    @Mock
    Authentication authentication;
    @Mock
    Jwt jwt;

    @InjectMocks
    CreatePaymentService createPaymentService;

    @BeforeEach
    void setUp() {
        createPaymentService = new CreatePaymentService(
                paymentRepository,
                serviceTestRepository,
                invoiceRepository,
                walletRepository,
                appointmentService,
                userRepository,
                walletTransactionRepository,
                entityManager
        );
    }

    @Test
    void testCreatePayment_success() {
        long paymentId = 1L;
        long serviceId = 2L;
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(100000);
        payment.setPaymentMethod(PaymentMethod.VN_PAY);
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(serviceId);
        serviceTest.setServiceName("Test Service");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);

        CreatePaymentRequest result = createPaymentService.createPayment(paymentId, serviceId);

        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice savedInvoice = invoiceCaptor.getValue();
        assertNotNull(savedInvoice.getTxnRef());
        assertEquals("Test Service", savedInvoice.getOrderInfo());
        assertEquals(TransactionStatus.PENDING, savedInvoice.getTransactionStatus());
        assertEquals((long) payment.getAmount(), savedInvoice.getAmount());
        assertEquals(payment, savedInvoice.getPayment());
        assertEquals(serviceTest, savedInvoice.getServiceTest());

        assertEquals(payment.getAmount(), result.getAmount());
        assertEquals("Test Service", result.getOrderInfo());
        assertEquals(savedInvoice.getTxnRef(), result.getTxnRef());
        assertEquals("http://localhost:5173/vnpay-payment", result.getReturnUrlBase());
    }

    @Test
    void testCreateWalletDeposit_success() {
        long userId = 10L;
        long depositAmount = 50000L;
        WalletRequest walletRequest = new WalletRequest();
        walletRequest.setAmount(depositAmount);

        Users user = new Users();
        user.setUserId(userId);

        Wallet wallet = new Wallet();
        wallet.setWalletId(100L);
        wallet.setUser(user);
        wallet.setBalance(0);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));

        ArgumentCaptor<WalletTransaction> transactionCaptor = ArgumentCaptor.forClass(WalletTransaction.class);

        CreatePaymentRequest result = createPaymentService.CreateWallet(authentication, walletRequest);

        verify(walletTransactionRepository).save(transactionCaptor.capture());
        WalletTransaction savedTransaction = transactionCaptor.getValue();
        assertEquals(wallet, savedTransaction.getWallet());
        assertEquals(TransactionType.DEPOSIT, savedTransaction.getType());
        assertEquals(depositAmount, savedTransaction.getAmount());
        assertEquals(TransactionStatus.PENDING, savedTransaction.getTransactionStatus());
        assertNotNull(savedTransaction.getTxnRef());

        assertEquals(depositAmount, result.getAmount());
        assertEquals("Nạp Tiền", result.getOrderInfo());
        assertEquals(savedTransaction.getTxnRef(), result.getTxnRef());
        assertEquals("http://localhost:5173/vnpay-payment/wallet", result.getReturnUrlBase());
    }

    @Test
    void testSuccessPayment_updatesStatusAndNotifies() {
        String vnpTxnRef = "txn123";
        String responseCode = "00";
        Invoice invoice = new Invoice();
        invoice.setTxnRef(vnpTxnRef);
        invoice.setTransactionStatus(TransactionStatus.PENDING);

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.FAILED);
        Appointment appointment = mock(Appointment.class);
        Staff staff = mock(Staff.class);
        when(appointment.getStaff()).thenReturn(staff);
        payment.setAppointment(appointment);
        invoice.setPayment(payment);

        when(invoiceRepository.findByTxnRef(vnpTxnRef)).thenReturn(Optional.of(invoice));

        createPaymentService.successPayment(vnpTxnRef, responseCode);

        assertEquals(TransactionStatus.SUCCESS, invoice.getTransactionStatus());
        assertEquals(responseCode, invoice.getResponseCode());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        verify(appointment).setNote("Đã thanh toán");
        verify(appointmentService).increaseStaffNotification(staff);
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void testCreatePayment_invalidIds_throwsException() {
        long paymentId = 1L;
        long serviceId = 2L;

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> {
            createPaymentService.createPayment(paymentId, serviceId);
        });
        assertEquals(ErrorCodeUser.PAYMENT_INFO_NOT_EXISTS, ex.getErrorCode());

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setPaymentMethod(PaymentMethod.VN_PAY);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.empty());

        AppException ex2 = assertThrows(AppException.class, () -> {
            createPaymentService.createPayment(paymentId, serviceId);
        });
        assertEquals(ErrorCodeUser.SERVICE_NOT_EXISTS, ex2.getErrorCode());
    }

    @Test
    void testCreatePayment_invalidPaymentMethod_throwsException() {
        long paymentId = 1L;
        long serviceId = 2L;
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setPaymentMethod(PaymentMethod.CASH);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            createPaymentService.createPayment(paymentId, serviceId);
        });
        assertEquals("Vui lòng chọn phương pháp thanh toán là Vnpay", ex.getMessage());
    }

    @Test
    void testSuccessPaymentWallet_alreadyProcessed_skipsUpdate() {
        String vnpTxnRef = "txn456";
        WalletTransaction walletTransaction = mock(WalletTransaction.class);

        when(walletTransactionRepository.findByTxnRef(vnpTxnRef)).thenReturn(Optional.of(walletTransaction));
        when(walletTransaction.getTransactionStatus()).thenReturn(TransactionStatus.SUCCESS);

        createPaymentService.successPaymentWallet(vnpTxnRef);

        verify(walletTransactionRepository, never()).save(any());
        verify(walletTransaction, never()).setTransactionStatus(TransactionStatus.SUCCESS);
    }
}