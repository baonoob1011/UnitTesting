package swp.project.adn_backend;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import swp.project.adn_backend.dto.InfoDTO.PriceInfoDTO;
import swp.project.adn_backend.dto.request.serviceRequest.AddPriceListRequest;
import swp.project.adn_backend.entity.Discount;
import swp.project.adn_backend.entity.PriceList;
import swp.project.adn_backend.entity.ServiceTest;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.AddPriceListMapper;
import swp.project.adn_backend.repository.ServiceTestRepository;
import swp.project.adn_backend.service.registerServiceTestService.AddPriceListService;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddPriceListServiceTest {

    @Mock
    private AddPriceListMapper addPriceListMapper;

    @Mock
    private ServiceTestRepository serviceTestRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AddPriceListService addPriceListService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        addPriceListService = new AddPriceListService(addPriceListMapper, serviceTestRepository, entityManager);
    }

    @Test
    void testAddMorePriceList_FirstEntry() {
        long serviceId = 1L;
        AddPriceListRequest request = new AddPriceListRequest(LocalDate.now(), "08:00-09:00", 100.0);
        ServiceTest serviceTest = mock(ServiceTest.class);
        PriceList newPriceList = mock(PriceList.class);

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        when(serviceTest.getPriceLists()).thenReturn(null);
        when(addPriceListMapper.toAddPriceList(request)).thenReturn(newPriceList);

        doNothing().when(serviceTest).setPriceLists(anyList());
        doNothing().when(newPriceList).setService(serviceTest);
        when(serviceTestRepository.save(serviceTest)).thenReturn(serviceTest);

        addPriceListService.addMorePriceList(request, serviceId);

        ArgumentCaptor<List<PriceList>> captor = ArgumentCaptor.forClass(List.class);
        verify(serviceTest).setPriceLists(captor.capture());
        List<PriceList> capturedList = captor.getValue();
        assertNotNull(capturedList);
        assertTrue(capturedList.contains(newPriceList));
        verify(newPriceList).setService(serviceTest);
        verify(serviceTestRepository).save(serviceTest);
    }

    @Test
    void testAddMorePriceList_AdditionalEntry() {
        long serviceId = 2L;
        AddPriceListRequest request = new AddPriceListRequest(LocalDate.now(), "09:00-10:00", 200.0);
        ServiceTest serviceTest = mock(ServiceTest.class);
        PriceList existingPriceList = mock(PriceList.class);
        PriceList newPriceList = mock(PriceList.class);

        List<PriceList> existingList = new ArrayList<>();
        existingList.add(existingPriceList);

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        when(serviceTest.getPriceLists()).thenReturn(existingList);
        when(addPriceListMapper.toAddPriceList(request)).thenReturn(newPriceList);

        doNothing().when(serviceTest).setPriceLists(anyList());
        doNothing().when(newPriceList).setService(serviceTest);
        when(serviceTestRepository.save(serviceTest)).thenReturn(serviceTest);

        addPriceListService.addMorePriceList(request, serviceId);

        ArgumentCaptor<List<PriceList>> captor = ArgumentCaptor.forClass(List.class);
        verify(serviceTest).setPriceLists(captor.capture());
        List<PriceList> capturedList = captor.getValue();
        assertEquals(2, capturedList.size());
        assertTrue(capturedList.contains(existingPriceList));
        assertTrue(capturedList.contains(newPriceList));
        verify(newPriceList).setService(serviceTest);
        verify(serviceTestRepository).save(serviceTest);
    }

    @Test
    void testGetAllPrice_WithActiveDiscounts() {
        long serviceId = 3L;
        ServiceTest serviceTest = mock(ServiceTest.class);

        Discount discount1 = mock(Discount.class);
        Discount discount2 = mock(Discount.class);
        when(discount1.isActive()).thenReturn(true);
        when(discount2.isActive()).thenReturn(true);
        when(discount1.getDiscountValue()).thenReturn(10.0);
        when(discount2.getDiscountValue()).thenReturn(5.0);

        List<Discount> discounts = Arrays.asList(discount1, discount2);

        PriceList priceList1 = mock(PriceList.class);
        PriceList priceList2 = mock(PriceList.class);
        when(priceList1.getPriceId()).thenReturn(1L);
        when(priceList1.getPrice()).thenReturn(100.0);
        when(priceList1.getTime()).thenReturn("08:00-09:00");
        when(priceList2.getPriceId()).thenReturn(2L);
        when(priceList2.getPrice()).thenReturn(200.0);
        when(priceList2.getTime()).thenReturn("09:00-10:00");

        List<PriceList> priceLists = Arrays.asList(priceList1, priceList2);

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        when(serviceTest.getDiscounts()).thenReturn(discounts);
        when(serviceTest.getPriceLists()).thenReturn(priceLists);

        List<PriceInfoDTO> result = addPriceListService.getAllPrice(serviceId);

        assertEquals(2, result.size());
        // Total discount = 15%
        assertEquals(85.0, result.get(0).getPrice(), 0.0001);
        assertEquals(170.0, result.get(1).getPrice(), 0.0001);
        assertEquals("08:00-09:00", result.get(0).getTime());
        assertEquals("09:00-10:00", result.get(1).getTime());
    }

    @Test
    void testAddMorePriceList_NonExistentService() {
        long serviceId = 999L;
        AddPriceListRequest request = new AddPriceListRequest(LocalDate.now(), "10:00-11:00", 300.0);

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> {
            addPriceListService.addMorePriceList(request, serviceId);
        });
        assertEquals(ErrorCodeUser.SERVICE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testGetAllPrice_NonExistentService() {
        long serviceId = 888L;
        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> {
            addPriceListService.getAllPrice(serviceId);
        });
        assertEquals(ErrorCodeUser.SERVICE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testGetAllPrice_NoDiscounts() {
        long serviceId = 4L;
        ServiceTest serviceTest = mock(ServiceTest.class);

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));
        when(serviceTest.getDiscounts()).thenReturn(Collections.emptyList());

        PriceList priceList1 = mock(PriceList.class);
        PriceList priceList2 = mock(PriceList.class);
        when(priceList1.getPriceId()).thenReturn(1L);
        when(priceList1.getPrice()).thenReturn(150.0);
        when(priceList1.getTime()).thenReturn("11:00-12:00");
        when(priceList2.getPriceId()).thenReturn(2L);
        when(priceList2.getPrice()).thenReturn(250.0);
        when(priceList2.getTime()).thenReturn("12:00-13:00");

        List<PriceList> priceLists = Arrays.asList(priceList1, priceList2);
        when(serviceTest.getPriceLists()).thenReturn(priceLists);

        List<PriceInfoDTO> result = addPriceListService.getAllPrice(serviceId);

        assertEquals(2, result.size());
        assertEquals(150.0, result.get(0).getPrice(), 0.0001);
        assertEquals(250.0, result.get(1).getPrice(), 0.0001);
        assertEquals("11:00-12:00", result.get(0).getTime());
        assertEquals("12:00-13:00", result.get(1).getTime());
    }
}