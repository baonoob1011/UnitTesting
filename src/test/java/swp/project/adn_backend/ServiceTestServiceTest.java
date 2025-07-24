package swp.project.adn_backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import swp.project.adn_backend.dto.request.serviceRequest.PriceListRequest;
import swp.project.adn_backend.dto.request.serviceRequest.ServiceRequest;
import swp.project.adn_backend.dto.request.updateRequest.UpdateServiceTestRequest;
import swp.project.adn_backend.dto.response.discount.DiscountResponse;
import swp.project.adn_backend.dto.response.serviceResponse.*;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.SampleCollectionMethod;
import swp.project.adn_backend.enums.ServiceType;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.*;
import swp.project.adn_backend.repository.*;

import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import swp.project.adn_backend.service.registerServiceTestService.ServiceTestService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTestServiceTest {

    @Mock UserRepository userRepository;
    @Mock ServiceTestMapper serviceTestMapper;
    @Mock ServiceTestRepository serviceTestRepository;
    @Mock PriceListMapper priceListMapper;
    @Mock FeedbackMapper feedbackMapper;
    @Mock AdministrativeMapper administrativeMapper;
    @Mock CivilServiceMapper civilServiceMapper;
    @Mock AdministrativeServiceRepository administrativeServiceRepository;
    @Mock CivilServiceRepository civilServiceRepository;
    @Mock PriceListRepository priceListRepository;
    @Mock EntityManager entityManager;
    @Mock UserMapper userMapper;
    @Mock ManagerRepository managerRepository;
    @Mock KitRepository kitRepository;
    @Mock MultipartFile multipartFile;
    private long startTime;

    ServiceTestService serviceTestService;

    @BeforeEach
    void setUp() {
        serviceTestService = new ServiceTestService(
                userRepository,
                serviceTestMapper,
                serviceTestRepository,
                priceListMapper,
                feedbackMapper,
                administrativeMapper,
                civilServiceMapper,
                administrativeServiceRepository,
                civilServiceRepository,
                priceListRepository,
                entityManager,
                userMapper,
                managerRepository,
                kitRepository
        );
    }

    @Test
    void testCreateService_Success() throws IOException {
        ServiceRequest serviceRequest = new ServiceRequest("Test Service", "Description", ServiceType.ADMINISTRATIVE, true);
        PriceListRequest priceListRequest = mock(PriceListRequest.class);
        long kitId = 1L;
        Kit kit = new Kit();
        ServiceTest mappedServiceTest = new ServiceTest();
        mappedServiceTest.setServiceName("Test Service");
        mappedServiceTest.setServiceType(ServiceType.ADMINISTRATIVE);
        mappedServiceTest.setActive(true);
        mappedServiceTest.setKit(kit);

        PriceList mappedPriceList = new PriceList();
        mappedPriceList.setPrice(100.0);
        mappedPriceList.setService(mappedServiceTest);

        when(serviceTestRepository.existsByServiceName("Test Service")).thenReturn(false);
        when(kitRepository.findById(kitId)).thenReturn(Optional.of(kit));
        when(serviceTestMapper.toServiceTest(serviceRequest)).thenReturn(mappedServiceTest);
        when(priceListMapper.toPriceList(priceListRequest)).thenReturn(mappedPriceList);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getBytes()).thenReturn("image".getBytes());
        when(serviceTestRepository.save(any(ServiceTest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceTest result = serviceTestService.createService(serviceRequest, priceListRequest, kitId, multipartFile);

        assertNotNull(result);
        assertEquals("Test Service", result.getServiceName());
        assertEquals(ServiceType.ADMINISTRATIVE, result.getServiceType());
        assertTrue(result.isActive());
        assertNotNull(result.getKit());
        assertNotNull(result.getImage());
        assertNotNull(result.getPriceLists());
        verify(administrativeServiceRepository).save(any(AdministrativeService.class));
    }

    @Test
    void testGetAllService_ReturnsAllServicesWithPriceLists() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceName("Service1");
        PriceList priceList = new PriceList();
        priceList.setTime("Morning");
        priceList.setPrice(200.0);
        serviceTest.setPriceLists(List.of(priceList));

        when(serviceTestRepository.findAll()).thenReturn(List.of(serviceTest));
        GetAllServiceResponse getAllServiceResponse = new GetAllServiceResponse();
        when(serviceTestMapper.toGetAllServiceTestResponse(serviceTest)).thenReturn(getAllServiceResponse);
        PriceListResponse priceListResponse = new PriceListResponse();
        when(priceListMapper.toPriceListResponse(priceList)).thenReturn(priceListResponse);

        List<FullServiceTestResponse> responses = serviceTestService.getAllService();

        assertEquals(1, responses.size());
        FullServiceTestResponse resp = responses.get(0);
        assertNotNull(resp.getServiceRequest());
        assertEquals(1, resp.getPriceListRequest().size());
    }

    @Test
    void testUpdateService_SuccessfulUpdate() throws IOException {
        long serviceId = 1L;
        UpdateServiceTestRequest updateRequest = new UpdateServiceTestRequest("Updated Name", LocalDate.now(), "Updated Desc", ServiceType.CIVIL, "img");
        Authentication authentication = mock(Authentication.class);
        PriceListRequest priceListRequest = new PriceListRequest();
        priceListRequest.setTime("Afternoon");
        priceListRequest.setPrice(300.0);
        priceListRequest.setEffectiveDate(LocalDate.now());
        MultipartFile file = mock(MultipartFile.class);

        ServiceTest existingService = new ServiceTest();
        existingService.setServiceId(serviceId);
        existingService.setServiceName("Old Name");
        existingService.setDescription("Old Desc");
        existingService.setServiceType(ServiceType.ADMINISTRATIVE);
        existingService.setActive(true);
        PriceList priceList = new PriceList();
        priceList.setPrice(100.0);
        priceList.setTime("Morning");
        priceList.setEffectiveDate(LocalDate.now().minusDays(1));
        existingService.setPriceLists(new ArrayList<>(List.of(priceList)));

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(file.isEmpty()).thenReturn(true); // No image update
        when(civilServiceRepository.findByService(existingService)).thenReturn(Optional.empty());
        when(serviceTestRepository.existsByServiceName("Updated Name")).thenReturn(false);
        when(serviceTestRepository.save(any(ServiceTest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceTest updated = serviceTestService.updateService(serviceId, updateRequest, authentication, priceListRequest, file);

        assertEquals("Updated Name", updated.getServiceName());
        assertEquals("Updated Desc", updated.getDescription());
        assertEquals(ServiceType.CIVIL, updated.getServiceType());
        assertEquals("Afternoon", updated.getPriceLists().get(0).getTime());
        assertEquals(300.0, updated.getPriceLists().get(0).getPrice());
    }

    @Test
    void testCreateService_DuplicateServiceName_ThrowsException() {
        ServiceRequest serviceRequest = new ServiceRequest("Duplicate", "desc", ServiceType.ADMINISTRATIVE, true);
        PriceListRequest priceListRequest = mock(PriceListRequest.class);
        long kitId = 1L;

        when(serviceTestRepository.existsByServiceName("Duplicate")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                serviceTestService.createService(serviceRequest, priceListRequest, kitId, null)
        );
        assertEquals(ErrorCodeUser.SERVICE_NAME_IS_EXISTED, ex.getErrorCode());
    }

    @Test
    void testCreateService_NonExistentKit_ThrowsException() {
        ServiceRequest serviceRequest = new ServiceRequest("Service", "desc", ServiceType.ADMINISTRATIVE, true);
        PriceListRequest priceListRequest = mock(PriceListRequest.class);
        long kitId = 99L;

        when(serviceTestRepository.existsByServiceName("Service")).thenReturn(false);
        when(kitRepository.findById(kitId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                serviceTestService.createService(serviceRequest, priceListRequest, kitId, null)
        );
        assertEquals(ErrorCodeUser.KIT_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testCreateService_CivilServiceType_Success() throws IOException {
        ServiceRequest serviceRequest = new ServiceRequest("Civil Test Service", "Civil Description", ServiceType.CIVIL, true);
        PriceListRequest priceListRequest = mock(PriceListRequest.class);
        long kitId = 1L;
        Kit kit = new Kit();
        ServiceTest mappedServiceTest = new ServiceTest();
        mappedServiceTest.setServiceName("Civil Test Service");
        mappedServiceTest.setServiceType(ServiceType.CIVIL);
        mappedServiceTest.setActive(true);
        mappedServiceTest.setKit(kit);

        PriceList mappedPriceList = new PriceList();
        mappedPriceList.setPrice(150.0);
        mappedPriceList.setService(mappedServiceTest);

        when(serviceTestRepository.existsByServiceName("Civil Test Service")).thenReturn(false);
        when(kitRepository.findById(kitId)).thenReturn(Optional.of(kit));
        when(serviceTestMapper.toServiceTest(serviceRequest)).thenReturn(mappedServiceTest);
        when(priceListMapper.toPriceList(priceListRequest)).thenReturn(mappedPriceList);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getBytes()).thenReturn("civil_image".getBytes());
        when(serviceTestRepository.save(any(ServiceTest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceTest result = serviceTestService.createService(serviceRequest, priceListRequest, kitId, multipartFile);

        assertNotNull(result);
        assertEquals("Civil Test Service", result.getServiceName());
        assertEquals(ServiceType.CIVIL, result.getServiceType());
        assertTrue(result.isActive());
        assertNotNull(result.getKit());
        assertNotNull(result.getImage());
        assertNotNull(result.getPriceLists());
        verify(civilServiceRepository).save(any(CivilService.class));
    }

    @Test
    void testGetCivilServices_ReturnsMappedCivilServices() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setActive(true);
        serviceTest.setServiceType(ServiceType.CIVIL);

        PriceList priceList = new PriceList();
        priceList.setTime("Morning");
        priceList.setPrice(100.0);
        serviceTest.setPriceLists(List.of(priceList));

        Discount discount = new Discount();
        discount.setActive(true);
        discount.setDiscountValue(10.0);
        serviceTest.setDiscounts(List.of(discount));

        CivilService civilService = new CivilService();
        Set<SampleCollectionMethod> methods = new HashSet<>(Arrays.asList(SampleCollectionMethod.AT_CLINIC, SampleCollectionMethod.AT_HOME));
        civilService.setSampleCollectionMethods(methods);
        serviceTest.setCivilServices(List.of(civilService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.CIVIL)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(1L, "Civil Service", "desc", ServiceType.CIVIL, "img");
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        DiscountResponse discountResponse = new DiscountResponse(1L, "Discount", 10.0, LocalDate.now(), LocalDate.now().plusDays(1), true);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(List.of(discountResponse));

        List<FullCivilServiceResponse> result = serviceTestService.getCivilServices();

        assertEquals(1, result.size());
        FullCivilServiceResponse resp = result.get(0);
        assertNotNull(resp.getServiceRequest());
        assertEquals(serviceTestResponse, resp.getServiceRequest());
        assertEquals(1, resp.getPriceListRequest().size());
        assertEquals("Morning", resp.getPriceListRequest().get(0).getTime());
        assertEquals(90.0, resp.getPriceListRequest().get(0).getPrice()); // 100 - 10%
        assertEquals(1, resp.getDiscountResponses().size());
        assertEquals(discountResponse, resp.getDiscountResponses().get(0));
        assertEquals(1, resp.getServiceResponses().size());
        assertEquals(methods, resp.getServiceResponses().get(0).getSampleCollectionMethods());
    }

    @Test
    void testGetCivilServices_AppliesFirstActiveDiscount() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setActive(true);
        serviceTest.setServiceType(ServiceType.CIVIL);

        PriceList priceList = new PriceList();
        priceList.setTime("Evening");
        priceList.setPrice(200.0);
        serviceTest.setPriceLists(List.of(priceList));

        Discount discount1 = new Discount();
        discount1.setActive(true);
        discount1.setDiscountValue(20.0);
        Discount discount2 = new Discount();
        discount2.setActive(true);
        discount2.setDiscountValue(50.0);
        serviceTest.setDiscounts(List.of(discount1, discount2));

        CivilService civilService = new CivilService();
        civilService.setSampleCollectionMethods(Set.of(SampleCollectionMethod.AT_CLINIC));
        serviceTest.setCivilServices(List.of(civilService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.CIVIL)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(2L, "Civil Service 2", "desc2", ServiceType.CIVIL, "img2");
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        DiscountResponse discountResponse1 = new DiscountResponse(2L, "Discount1", 20.0, LocalDate.now(), LocalDate.now().plusDays(1), true);
        DiscountResponse discountResponse2 = new DiscountResponse(3L, "Discount2", 50.0, LocalDate.now(), LocalDate.now().plusDays(2), true);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(List.of(discountResponse1, discountResponse2));

        List<FullCivilServiceResponse> result = serviceTestService.getCivilServices();

        assertEquals(1, result.size());
        FullCivilServiceResponse resp = result.get(0);
        assertEquals(1, resp.getPriceListRequest().size());
        PriceListResponse priceResp = resp.getPriceListRequest().get(0);
        assertEquals("Evening", priceResp.getTime());
        assertEquals(160.0, priceResp.getPrice()); // 200 - 20% = 160, only first active discount applied
        assertEquals(200.0, priceResp.getPriceTmp());
    }

    @Test
    void testGetCivilServices_MultipleCivilServicesHandled() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setActive(true);
        serviceTest.setServiceType(ServiceType.CIVIL);

        CivilService civilService1 = new CivilService();
        civilService1.setSampleCollectionMethods(Set.of(SampleCollectionMethod.AT_CLINIC));
        CivilService civilService2 = new CivilService();
        civilService2.setSampleCollectionMethods(Set.of(SampleCollectionMethod.AT_HOME));
        serviceTest.setCivilServices(List.of(civilService1, civilService2));

        serviceTest.setPriceLists(Collections.emptyList());
        serviceTest.setDiscounts(Collections.emptyList());

        when(serviceTestRepository.findAllByServiceType(ServiceType.CIVIL)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(3L, "Civil Service 3", "desc3", ServiceType.CIVIL, "img3");
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(Collections.emptyList());

        List<FullCivilServiceResponse> result = serviceTestService.getCivilServices();

        assertEquals(1, result.size());
        FullCivilServiceResponse resp = result.get(0);
        assertEquals(2, resp.getServiceResponses().size());
        Set<SampleCollectionMethod> allMethods = new HashSet<>();
        for (CivilServiceResponse csr : resp.getServiceResponses()) {
            allMethods.addAll(csr.getSampleCollectionMethods());
        }
        assertTrue(allMethods.contains(SampleCollectionMethod.AT_CLINIC));
        assertTrue(allMethods.contains(SampleCollectionMethod.AT_HOME));
    }

    @Test
    void testGetCivilServices_NoCivilServices_ReturnsEmptyList() {
        when(serviceTestRepository.findAllByServiceType(ServiceType.CIVIL)).thenReturn(Collections.emptyList());
        List<FullCivilServiceResponse> result = serviceTestService.getCivilServices();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCivilServices_NullOrEmptyPriceListsAndDiscounts() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setActive(true);
        serviceTest.setServiceType(ServiceType.CIVIL);

        // priceLists and discounts are null
        serviceTest.setPriceLists(null);
        serviceTest.setDiscounts(null);

        CivilService civilService = new CivilService();
        civilService.setSampleCollectionMethods(Set.of(SampleCollectionMethod.AT_CLINIC));
        serviceTest.setCivilServices(List.of(civilService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.CIVIL)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(4L, "Civil Service 4", "desc4", ServiceType.CIVIL, "img4");
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        when(serviceTestMapper.toDiscountResponses(null)).thenReturn(Collections.emptyList());

        List<FullCivilServiceResponse> result = serviceTestService.getCivilServices();

        assertEquals(1, result.size());
        FullCivilServiceResponse resp = result.get(0);
        assertNotNull(resp.getServiceRequest());
        assertTrue(resp.getPriceListRequest().isEmpty());
        assertTrue(resp.getDiscountResponses().isEmpty());
    }

    @Test
    void testGetCivilServices_SkipsInactiveServices() {
        ServiceTest activeService = new ServiceTest();
        activeService.setActive(true);
        activeService.setServiceType(ServiceType.CIVIL);
        activeService.setPriceLists(Collections.emptyList());
        activeService.setDiscounts(Collections.emptyList());
        activeService.setCivilServices(Collections.emptyList());

        ServiceTest inactiveService = new ServiceTest();
        inactiveService.setActive(false);
        inactiveService.setServiceType(ServiceType.CIVIL);
        inactiveService.setPriceLists(Collections.emptyList());
        inactiveService.setDiscounts(Collections.emptyList());
        inactiveService.setCivilServices(Collections.emptyList());

        when(serviceTestRepository.findAllByServiceType(ServiceType.CIVIL))
                .thenReturn(List.of(activeService, inactiveService));

        ServiceTestResponse activeServiceResponse = new ServiceTestResponse(5L, "Active Civil Service", "desc5", ServiceType.CIVIL, "img5");

        when(serviceTestMapper.toServiceTestResponse(activeService)).thenReturn(activeServiceResponse);
        when(serviceTestMapper.toDiscountResponses(activeService.getDiscounts())).thenReturn(Collections.emptyList());

        List<FullCivilServiceResponse> result = serviceTestService.getCivilServices();

        assertEquals(1, result.size()); // ✅ Only active service included
        assertEquals(activeServiceResponse, result.get(0).getServiceRequest());
    }


    @Test
    void testGetAdministrativeServices_MapsAllFieldsCorrectly() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(1L);
        serviceTest.setServiceName("Admin Service");
        serviceTest.setDescription("Desc");
        serviceTest.setServiceType(ServiceType.ADMINISTRATIVE);
        serviceTest.setImage("img");
        // Discounts
        Discount discount = new Discount();
        discount.setDiscountId(10L);
        discount.setDiscountName("Spring Sale");
        discount.setDiscountValue(15.0);
        discount.setStartDate(LocalDate.now());
        discount.setEndDate(LocalDate.now().plusDays(5));
        discount.setActive(true);
        serviceTest.setDiscounts(List.of(discount));
        // PriceList
        PriceList priceList = new PriceList();
        priceList.setTime("Morning");
        priceList.setPrice(200.0);
        serviceTest.setPriceLists(List.of(priceList));
        // AdministrativeService
        AdministrativeService adminService = new AdministrativeService();
        adminService.setSampleCollectionMethod(SampleCollectionMethod.AT_CLINIC);
        serviceTest.setAdministrativeService(List.of(adminService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.ADMINISTRATIVE)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(1L, "Admin Service", "Desc", ServiceType.ADMINISTRATIVE, "img");
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        DiscountResponse discountResponse = new DiscountResponse(10L, "Spring Sale", 15.0, discount.getStartDate(), discount.getEndDate(), true);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(List.of(discountResponse));

        List<FullAdministrationServiceResponse> result = serviceTestService.getAdministrativeServices();

        assertEquals(1, result.size());
        FullAdministrationServiceResponse resp = result.get(0);
        assertEquals(serviceTestResponse, resp.getServiceRequest());
        assertEquals(1, resp.getDiscountResponses().size());
        assertEquals(discountResponse, resp.getDiscountResponses().get(0));
        assertEquals(1, resp.getPriceListRequest().size());
        PriceListResponse priceResp = resp.getPriceListRequest().get(0);
        assertEquals("Morning", priceResp.getTime());
        assertEquals(200.0 * (1 - 0.15), priceResp.getPrice());
        assertEquals(200.0, priceResp.getPriceTmp());
        assertEquals(1, resp.getAdministrativeServiceRequest().size());
        assertEquals(SampleCollectionMethod.AT_CLINIC, resp.getAdministrativeServiceRequest().get(0).getSampleCollectionMethod());
    }

    @Test
    void testGetAdministrativeServices_NoDiscounts_PriceCalculation() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(2L);
        serviceTest.setServiceName("No Discount Admin");
        serviceTest.setServiceType(ServiceType.ADMINISTRATIVE);
        // No discounts
        serviceTest.setDiscounts(Collections.emptyList());
        // PriceList
        PriceList priceList = new PriceList();
        priceList.setTime("Afternoon");
        priceList.setPrice(300.0);
        serviceTest.setPriceLists(List.of(priceList));
        // AdministrativeService
        AdministrativeService adminService = new AdministrativeService();
        adminService.setSampleCollectionMethod(SampleCollectionMethod.AT_HOME);
        serviceTest.setAdministrativeService(List.of(adminService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.ADMINISTRATIVE)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(2L, "No Discount Admin", null, ServiceType.ADMINISTRATIVE, null);
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(Collections.emptyList());

        List<FullAdministrationServiceResponse> result = serviceTestService.getAdministrativeServices();

        assertEquals(1, result.size());
        FullAdministrationServiceResponse resp = result.get(0);
        assertEquals(1, resp.getPriceListRequest().size());
        PriceListResponse priceResp = resp.getPriceListRequest().get(0);
        assertEquals("Afternoon", priceResp.getTime());
        assertEquals(300.0, priceResp.getPrice());
        assertEquals(300.0, priceResp.getPriceTmp());
        assertTrue(resp.getDiscountResponses().isEmpty());
    }

    @Test
    void testGetAdministrativeServices_MultiplePriceListsHandled() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceType(ServiceType.ADMINISTRATIVE);
        serviceTest.setDiscounts(Collections.emptyList());
        PriceList price1 = new PriceList();
        price1.setTime("Morning");
        price1.setPrice(100.0);
        PriceList price2 = new PriceList();
        price2.setTime("Evening");
        price2.setPrice(150.0);
        serviceTest.setPriceLists(List.of(price1, price2));
        AdministrativeService adminService = new AdministrativeService();
        adminService.setSampleCollectionMethod(SampleCollectionMethod.AT_CLINIC);
        serviceTest.setAdministrativeService(List.of(adminService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.ADMINISTRATIVE)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(3L, "Multi Price Admin", null, ServiceType.ADMINISTRATIVE, null);
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(Collections.emptyList());

        List<FullAdministrationServiceResponse> result = serviceTestService.getAdministrativeServices();

        assertEquals(1, result.size());
        FullAdministrationServiceResponse resp = result.get(0);
        assertEquals(2, resp.getPriceListRequest().size());
        Set<String> times = new HashSet<>();
        Set<Double> prices = new HashSet<>();
        for (PriceListResponse pr : resp.getPriceListRequest()) {
            times.add(pr.getTime());
            prices.add(pr.getPrice());
        }
        assertTrue(times.contains("Morning"));
        assertTrue(times.contains("Evening"));
        assertTrue(prices.contains(100.0));
        assertTrue(prices.contains(150.0));
    }

    @Test
    void testGetAdministrativeServices_NullAdministrativeServiceList() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceType(ServiceType.ADMINISTRATIVE);
        serviceTest.setDiscounts(Collections.emptyList());
        PriceList priceList = new PriceList();
        priceList.setTime("Noon");
        priceList.setPrice(120.0);
        serviceTest.setPriceLists(List.of(priceList));
        // AdministrativeService is null
        serviceTest.setAdministrativeService(null);

        when(serviceTestRepository.findAllByServiceType(ServiceType.ADMINISTRATIVE)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(4L, "Null Admin List", null, ServiceType.ADMINISTRATIVE, null);
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(Collections.emptyList());

        List<FullAdministrationServiceResponse> result = serviceTestService.getAdministrativeServices();

        assertEquals(1, result.size());
        FullAdministrationServiceResponse resp = result.get(0);
        assertNotNull(resp.getAdministrativeServiceRequest());
        assertTrue(resp.getAdministrativeServiceRequest().isEmpty());
    }

    @Test
    void testGetAdministrativeServices_PriceListContainsNullValues() {
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceType(ServiceType.ADMINISTRATIVE);
        serviceTest.setDiscounts(Collections.emptyList());
        PriceList price1 = new PriceList();
        price1.setTime("Morning");
        price1.setPrice(100.0);
        // Add a null price list entry - this will cause NullPointerException in stream processing
        serviceTest.setPriceLists(Arrays.asList(price1, null));
        AdministrativeService adminService = new AdministrativeService();
        adminService.setSampleCollectionMethod(SampleCollectionMethod.AT_CLINIC);
        serviceTest.setAdministrativeService(List.of(adminService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.ADMINISTRATIVE)).thenReturn(List.of(serviceTest));
        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(5L, "Null Price Entry", null, ServiceType.ADMINISTRATIVE, null);
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        when(serviceTestMapper.toDiscountResponses(serviceTest.getDiscounts())).thenReturn(Collections.emptyList());

        // This should throw NullPointerException due to null entry in price list
        assertThrows(NullPointerException.class, () -> {
            serviceTestService.getAdministrativeServices();
        });
    }

    @Test
    void testGetAdministrativeServices_NullDiscountsList() {
        // Arrange
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(6L);
        serviceTest.setServiceName("Null Discounts");
        serviceTest.setServiceType(ServiceType.ADMINISTRATIVE);
        serviceTest.setDiscounts(null);

        PriceList priceList = new PriceList();
        priceList.setTime("Night");
        priceList.setPrice(180.0);
        serviceTest.setPriceLists(List.of(priceList));

        AdministrativeService adminService = new AdministrativeService();
        adminService.setSampleCollectionMethod(SampleCollectionMethod.AT_HOME);
        serviceTest.setAdministrativeService(List.of(adminService));

        when(serviceTestRepository.findAllByServiceType(ServiceType.ADMINISTRATIVE)).thenReturn(List.of(serviceTest));

        ServiceTestResponse serviceTestResponse = new ServiceTestResponse(
                6L,
                "Null Discounts",
                null,
                ServiceType.ADMINISTRATIVE,
                null
        );
        when(serviceTestMapper.toServiceTestResponse(serviceTest)).thenReturn(serviceTestResponse);
        when(serviceTestMapper.toDiscountResponses(null)).thenReturn(Collections.emptyList());

        // Act
        List<FullAdministrationServiceResponse> result = serviceTestService.getAdministrativeServices();

        // Assert
        assertEquals(1, result.size());
        FullAdministrationServiceResponse resp = result.get(0);

        assertNotNull(resp.getDiscountResponses(), "DiscountResponses should not be null");
        assertTrue(resp.getDiscountResponses().isEmpty(), "DiscountResponses should be empty when original discounts are null");

        // Optional additional asserts
        assertEquals("Null Discounts", resp.getServiceRequest().getServiceName());
        assertEquals(ServiceType.ADMINISTRATIVE, resp.getServiceRequest().getServiceType());
    }

    @Test
    void testDeleteServiceTest_ServiceRemainsInRepository() {
        long serviceId = 123L;
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(serviceId);
        serviceTest.setActive(true);

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        serviceTestService.deleteServiceTest(serviceId);

        // The service should not be deleted from the repository, only deactivated
        verify(serviceTestRepository, never()).delete(any());
        assertFalse(serviceTest.isActive());
    }

    @Test
    void testDeleteServiceTest_ActiveStatusChangedToInactive() {
        long serviceId = 456L;
        ServiceTest serviceTest = new ServiceTest();
        serviceTest.setServiceId(serviceId);
        serviceTest.setActive(true);

        when(serviceTestRepository.findById(serviceId)).thenReturn(Optional.of(serviceTest));

        serviceTestService.deleteServiceTest(serviceId);

        assertFalse(serviceTest.isActive(), "ServiceTest should be inactive after deletion");
    }

    @Test
    void testDeleteServiceTest_TransactionalRollbackOnException() {
        long serviceId = 789L;
        // Simulate not found, so exception is thrown
        when(serviceTestRepository.findById(serviceId)).thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            serviceTestService.deleteServiceTest(serviceId);
        });
        assertEquals("DB error", ex.getMessage());
        // Transactional rollback is handled by Spring, so here we just ensure the exception is propagated
    }

    @Test
    void testDeleteServiceTest_NegativeId_ThrowsException() {
        long negativeId = -1L;
        when(serviceTestRepository.findById(negativeId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> {
            serviceTestService.deleteServiceTest(negativeId);
        });
        assertEquals(ErrorCodeUser.SERVICE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testDeleteServiceTest_ZeroId_ThrowsException() {
        long zeroId = 0L;
        when(serviceTestRepository.findById(zeroId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> {
            serviceTestService.deleteServiceTest(zeroId);
        });
        assertEquals(ErrorCodeUser.SERVICE_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testDeleteServiceTest_RepositoryThrowsException_Propagates() {
        long serviceId = 999L;
        when(serviceTestRepository.findById(serviceId)).thenThrow(new RuntimeException("Unexpected error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            serviceTestService.deleteServiceTest(serviceId);
        });
        assertEquals("Unexpected error", ex.getMessage());
    }


}

