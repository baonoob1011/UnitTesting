package swp.project.adn_backend.service.registerServiceTestService;


import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import swp.project.adn_backend.dto.InfoDTO.AppointmentAtHomeInfoDTO;
import swp.project.adn_backend.dto.InfoDTO.ServiceFeedbackInfoDTO;
import swp.project.adn_backend.dto.request.serviceRequest.PriceListRequest;
import swp.project.adn_backend.dto.request.serviceRequest.ServiceRequest;
import swp.project.adn_backend.dto.request.updateRequest.UpdateServiceTestRequest;
import swp.project.adn_backend.dto.response.discount.DiscountResponse;
import swp.project.adn_backend.dto.response.serviceResponse.*;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.*;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.*;
import swp.project.adn_backend.repository.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceTestService {

    UserRepository userRepository;
    ServiceTestMapper serviceTestMapper;
    ServiceTestRepository serviceTestRepository;
    PriceListMapper priceListMapper;
    FeedbackMapper feedbackMapper;
    AdministrativeMapper administrativeMapper;
    CivilServiceMapper civilServiceMapper;
    AdministrativeServiceRepository administrativeServiceRepository;
    CivilServiceRepository civilServiceRepository;
    PriceListRepository priceListRepository;
    EntityManager entityManager;
    UserMapper userMapper;
    ManagerRepository managerRepository;
    KitRepository kitRepository;

    @Autowired
    public ServiceTestService(UserRepository userRepository, ServiceTestMapper serviceTestMapper, ServiceTestRepository serviceTestRepository, PriceListMapper priceListMapper, FeedbackMapper feedbackMapper, AdministrativeMapper administrativeMapper, CivilServiceMapper civilServiceMapper, AdministrativeServiceRepository administrativeServiceRepository, CivilServiceRepository civilServiceRepository, PriceListRepository priceListRepository, EntityManager entityManager, UserMapper userMapper, ManagerRepository managerRepository, KitRepository kitRepository) {
        this.userRepository = userRepository;
        this.serviceTestMapper = serviceTestMapper;
        this.serviceTestRepository = serviceTestRepository;
        this.priceListMapper = priceListMapper;
        this.feedbackMapper = feedbackMapper;
        this.administrativeMapper = administrativeMapper;
        this.civilServiceMapper = civilServiceMapper;
        this.administrativeServiceRepository = administrativeServiceRepository;
        this.civilServiceRepository = civilServiceRepository;
        this.priceListRepository = priceListRepository;
        this.entityManager = entityManager;
        this.userMapper = userMapper;
        this.managerRepository = managerRepository;
        this.kitRepository = kitRepository;
    }

    public ServiceTestService(ServiceType serviceType) {
    }

    public ServiceTest createService(ServiceRequest serviceRequest,
                                     PriceListRequest priceListRequest,
                                     long kitId,
                                     MultipartFile file) {


        if (serviceTestRepository.existsByServiceName(serviceRequest.getServiceName())) {
            throw new AppException(ErrorCodeUser.SERVICE_NAME_IS_EXISTED);
        }
        //find kid
        Kit kit = kitRepository.findById(kitId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.KIT_NOT_EXISTS));

        // Map ServiceTest
        ServiceTest serviceTest = serviceTestMapper.toServiceTest(serviceRequest);
        serviceTest.setRegisterDate(LocalDate.now());
        serviceTest.setKit(kit);
        serviceTest.setActive(true);

        // Upload image if present
        if (file != null && !file.isEmpty()) {
            try {
                String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
                serviceTest.setImage(base64Image);
            } catch (IOException e) {
                throw new AppException(ErrorCodeUser.INTERNAL_ERROR);
            }
        }

        // Price list
        PriceList priceList = priceListMapper.toPriceList(priceListRequest);
        priceList.setEffectiveDate(LocalDate.now());
        priceList.setService(serviceTest);
        serviceTest.setPriceLists(new ArrayList<>(List.of(priceList)));

        // Branch logic by service type
        switch (serviceRequest.getServiceType()) {
            case ADMINISTRATIVE -> {
                AdministrativeService administrativeService = new AdministrativeService();
                administrativeService.setSampleCollectionMethod(SampleCollectionMethod.AT_CLINIC);
                administrativeService.setService(serviceTest);
                administrativeServiceRepository.save(administrativeService);
            }

            case CIVIL -> {
                CivilService civilService = new CivilService();
                Set<SampleCollectionMethod> defaultMethods = new HashSet<>();
                defaultMethods.add(SampleCollectionMethod.AT_CLINIC);
                defaultMethods.add(SampleCollectionMethod.AT_HOME);
                civilService.setSampleCollectionMethods(defaultMethods);
                civilService.setService(serviceTest);
                civilServiceRepository.save(civilService);
            }

            default -> throw new AppException(ErrorCodeUser.INVALID_REQUEST);
        }

        return serviceTestRepository.save(serviceTest);
    }

    public List<FullServiceTestResponse> getAllService() {
        List<ServiceTest> serviceTests = serviceTestRepository.findAll();
        List<FullServiceTestResponse> responses = new ArrayList<>();
        FullServiceTestResponse fullServiceTestResponse = null;
        for (ServiceTest serviceTest : serviceTests) {
            GetAllServiceResponse getAllServiceResponse = serviceTestMapper.toGetAllServiceTestResponse(serviceTest);

            // get Price List
            List<PriceListResponse> priceListResponses = new ArrayList<>();

            for (PriceList priceList : serviceTest.getPriceLists()) {
                PriceListResponse priceListResponse = priceListMapper.toPriceListResponse(priceList);
                priceListResponses.add(priceListResponse);
            }
            fullServiceTestResponse = new FullServiceTestResponse();
            fullServiceTestResponse.setServiceRequest(getAllServiceResponse);
            fullServiceTestResponse.setPriceListRequest(priceListResponses);
            responses.add(fullServiceTestResponse);
        }
        return responses;
    }

    public List<FullAdministrationServiceResponse> getAdministrativeServices() {
        List<ServiceTest> services = serviceTestRepository.findAllByServiceType(ServiceType.ADMINISTRATIVE);

        return services.stream().map(service -> {
            // Basic mapping
            ServiceTestResponse serviceResponse = serviceTestMapper.toServiceTestResponse(service);
            List<DiscountResponse> discountResponses = serviceTestMapper.toDiscountResponses(service.getDiscounts());

            // Map price list
            List<PriceListResponse> priceResponses = Optional.ofNullable(service.getPriceLists())
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(price -> {
                        PriceListResponse priceResponse = new PriceListResponse();
                        priceResponse.setTime(price.getTime());
                        priceResponse.setPriceTmp(price.getPrice());

                        // Safe handling of null discounts
                        List<Discount> discounts = Optional.ofNullable(service.getDiscounts()).orElse(Collections.emptyList());

                        double finalPrice = discounts.stream()
                                .filter(Discount::isActive)
                                .findFirst()
                                .map(discount -> price.getPrice() * (1 - discount.getDiscountValue() / 100.0))
                                .orElse(price.getPrice());

                        priceResponse.setPrice(finalPrice);
                        return priceResponse;
                    })
                    .collect(Collectors.toList());

            // Map administrative services
            List<AdministrativeServiceResponse> adminResponses = Optional.ofNullable(service.getAdministrativeService())
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(admin -> {
                        AdministrativeServiceResponse adminResponse = new AdministrativeServiceResponse();
                        adminResponse.setSampleCollectionMethod(admin.getSampleCollectionMethod());
                        return adminResponse;
                    })
                    .collect(Collectors.toList());

            // Combine to full response
            FullAdministrationServiceResponse fullResponse = new FullAdministrationServiceResponse();
            fullResponse.setServiceRequest(serviceResponse);
            fullResponse.setDiscountResponses(discountResponses);
            fullResponse.setPriceListRequest(priceResponses);
            fullResponse.setAdministrativeServiceRequest(adminResponses);

            return fullResponse;
        }).collect(Collectors.toList());
    }


    public List<FullCivilServiceResponse> getCivilServices() {
        List<ServiceTest> services = serviceTestRepository.findAllByServiceType(ServiceType.CIVIL);
        List<FullCivilServiceResponse> responses = new ArrayList<>();

        for (ServiceTest s : services) {
            if (!s.isActive()) {
                continue; // Skip inactive services
            }

            // Convert service entity to DTO
            ServiceTestResponse serviceReq = serviceTestMapper.toServiceTestResponse(s);
            List<DiscountResponse> discountResponses = serviceTestMapper.toDiscountResponses(s.getDiscounts());

            // Convert price list
            List<PriceListResponse> priceReqs = new ArrayList<>();
            for (PriceList p : Optional.ofNullable(s.getPriceLists()).orElse(Collections.emptyList())) {
                PriceListResponse res = new PriceListResponse();
                res.setTime(p.getTime());
                res.setPriceTmp(p.getPrice());

                // Apply first active discount if any
                double finalPrice = p.getPrice();
                for (Discount discount : Optional.ofNullable(s.getDiscounts()).orElse(Collections.emptyList())) {
                    if (discount.isActive()) {
                        finalPrice = p.getPrice() * (1 - discount.getDiscountValue() / 100.0);
                        break;
                    }
                }

                res.setPrice(finalPrice);
                priceReqs.add(res);
            }

            // Convert civil services
            List<CivilServiceResponse> serviceResponses = new ArrayList<>();
            for (CivilService civilService : Optional.ofNullable(s.getCivilServices()).orElse(Collections.emptyList())) {
                CivilServiceResponse response = new CivilServiceResponse();
                response.setSampleCollectionMethods(civilService.getSampleCollectionMethods());
                serviceResponses.add(response);
            }

            // Build full response
            FullCivilServiceResponse fullResp = new FullCivilServiceResponse();
            fullResp.setServiceRequest(serviceReq);
            fullResp.setPriceListRequest(priceReqs);
            fullResp.setServiceResponses(serviceResponses);
            fullResp.setDiscountResponses(discountResponses);
            responses.add(fullResp);
        }

        return responses;
    }



    @Transactional
    public void deleteServiceTest(long serviceId) {
        ServiceTest serviceTest = serviceTestRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.SERVICE_NOT_EXISTS));
        serviceTest.setActive(false);
    }

    public List<ServiceFeedbackInfoDTO> getServiceById(long serviceId) {
        String jpql = "SELECT new swp.project.adn_backend.dto.InfoDTO.ServiceFeedbackInfoDTO(" +
                "s.serviceId, s.serviceName, s.description, s.serviceType, s.image) " +
                "FROM ServiceTest s " +
                "WHERE s.serviceId = :serviceId";
        TypedQuery<ServiceFeedbackInfoDTO> query = entityManager.createQuery(jpql, ServiceFeedbackInfoDTO.class);
        query.setParameter("serviceId", serviceId);
        return query.getResultList();
    }

    @Transactional
    public ServiceTest updateService(long serviceId,
                                     UpdateServiceTestRequest updateServiceTest,
                                     Authentication authentication,
                                     PriceListRequest priceListRequest,
                                     MultipartFile file
    ) {


        System.out.println("Service ID " + serviceId);
        ServiceTest existingService = serviceTestRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.SERVICE_NOT_EXISTS));


        if (updateServiceTest.getServiceName() != null) {
            if (!existingService.getServiceName().equals(updateServiceTest.getServiceName()) &&
                    serviceTestRepository.existsByServiceName(updateServiceTest.getServiceName())) {
                throw new AppException(ErrorCodeUser.SERVICE_NAME_IS_EXISTED);
            }
        }

        if (updateServiceTest.getDescription() != null) {
            existingService.setDescription(updateServiceTest.getDescription());
        }
        if (updateServiceTest.getServiceName() != null) {
            existingService.setServiceName(updateServiceTest.getServiceName());
        }
        existingService.setServiceId(existingService.getServiceId());
        existingService.setRegisterDate(LocalDate.now());
        if (file != null && !file.isEmpty()) {
            try {
                String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
                existingService.setImage(base64Image);
            } catch (IOException e) {
                throw new AppException(ErrorCodeUser.INTERNAL_ERROR);
            }
        }

        // Cập nhật PriceList mới
        if (priceListRequest != null && existingService.getPriceLists() != null) {
            List<PriceList> existingPriceLists = existingService.getPriceLists();
            for (PriceList priceList : existingPriceLists) {
                if (priceListRequest.getTime() != null) {
                    priceList.setTime(priceListRequest.getTime());
                }
                if (priceListRequest.getPrice() != null && Double.compare(priceListRequest.getPrice(), priceList.getPrice()) != 0) {
                    priceList.setPrice(priceListRequest.getPrice());
                }

                if (priceListRequest.getEffectiveDate() != null) {
                    priceList.setEffectiveDate(priceListRequest.getEffectiveDate());
                }
            }
        }

        if (updateServiceTest.getServiceType() == null) {
            throw new AppException(ErrorCodeUser.INVALID_REQUEST);
        }
        existingService.setServiceType(updateServiceTest.getServiceType());
        switch (updateServiceTest.getServiceType()) {
            case ADMINISTRATIVE -> {
                AdministrativeService existing = administrativeServiceRepository.findByService(existingService)
                        .orElse(new AdministrativeService());
                existing.setSampleCollectionMethod(SampleCollectionMethod.AT_CLINIC);
                existing.setService(existingService);
                administrativeServiceRepository.save(existing);
            }

            case CIVIL -> {
                CivilService existing = civilServiceRepository.findByService(existingService)
                        .orElse(new CivilService());
                Set<SampleCollectionMethod> defaultMethods = new HashSet<>();
                defaultMethods.add(SampleCollectionMethod.AT_CLINIC);
                defaultMethods.add(SampleCollectionMethod.AT_HOME);
                existing.setSampleCollectionMethods(defaultMethods);
                existing.setService(existingService);
                civilServiceRepository.save(existing);
            }

            default -> throw new AppException(ErrorCodeUser.INVALID_REQUEST);
        }

        return serviceTestRepository.save(existingService);
    }
}
