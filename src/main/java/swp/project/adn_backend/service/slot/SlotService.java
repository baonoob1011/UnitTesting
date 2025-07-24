package swp.project.adn_backend.service.slot;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swp.project.adn_backend.dto.request.slot.StaffSlotRequest;
import swp.project.adn_backend.dto.InfoDTO.SlotInfoDTO;
import swp.project.adn_backend.dto.request.slot.*;
//import swp.project.adn_backend.dto.response.SlotReponse;
import swp.project.adn_backend.dto.response.slot.GetFullSlotResponse;
import swp.project.adn_backend.dto.response.slot.RoomSlotResponse;
import swp.project.adn_backend.dto.response.slot.SlotResponse;
import swp.project.adn_backend.dto.response.slot.StaffSlotResponse;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.PaymentStatus;
import swp.project.adn_backend.enums.SlotStatus;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.SlotMapper;
import swp.project.adn_backend.repository.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SlotService {
    SlotMapper slotMapper;
    SlotRepository slotRepository;
    UserRepository userRepository;
    StaffRepository staffRepository;
    EntityManager entityManager;
    RoomRepository roomRepository;
    NotificationRepository notificationRepository;
    LocationRepository locationRepository;


    @Autowired
    public SlotService(SlotMapper slotMapper, SlotRepository slotRepository, UserRepository userRepository, StaffRepository staffRepository, EntityManager entityManager, RoomRepository roomRepository, NotificationRepository notificationRepository, LocationRepository locationRepository) {
        this.slotMapper = slotMapper;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
        this.entityManager = entityManager;
        this.roomRepository = roomRepository;
        this.notificationRepository = notificationRepository;
        this.locationRepository = locationRepository;
    }

    public List<SlotResponse> createSlot(SlotRequest slotRequest, long roomId, List<StaffSlotRequest> staffSlotRequests) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.ROOM_NOT_FOUND));

        List<Slot> createdSlots = new ArrayList<>();
        LocalDate currentDate = slotRequest.getSlotDate();
        LocalDate endDate = currentDate.plusDays(29); // 30 ngày

        while (!currentDate.isAfter(endDate)) {
            DayOfWeek day = currentDate.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            LocalTime slotStart = slotRequest.getStartTime();
            LocalTime slotEnd = slotStart.plusMinutes(30);

            while (!slotEnd.isAfter(room.getCloseTime())) {
                // Kiểm tra trùng slot trong room
                Integer roomOverlap = slotRepository.isSlotOverlappingNative(
                        roomId, currentDate, slotStart, slotEnd);
                if (roomOverlap != null && roomOverlap == 1) {
                    slotStart = slotStart.plusMinutes(30);
                    slotEnd = slotStart.plusMinutes(30);
                    continue;
                }

                // Kiểm tra slot trong giờ hoạt động
                if (slotStart.isBefore(room.getOpenTime()) || slotEnd.isAfter(room.getCloseTime())) {
                    break;
                }

                // Danh sách nhân viên hợp lệ
                List<Staff> staffList = new ArrayList<>();
                boolean skipSlot = false;
                for (StaffSlotRequest staffSlotRequest : staffSlotRequests) {
                    Staff staff = staffRepository.findById(staffSlotRequest.getStaffId())
                            .orElseThrow(() -> new AppException(ErrorCodeUser.STAFF_NOT_EXISTED));

                    Integer staffOverlap = slotRepository.isStaffOverlappingSlot(
                            staff.getStaffId(), currentDate, slotStart, slotEnd);
                    if (staffOverlap != null && staffOverlap > 0) {
                        skipSlot = true;
                        break; // Nhân viên đã có lịch => bỏ slot này
                    }

                    staffList.add(staff);
                }

                if (!skipSlot) {
                    SlotRequest newRequest = new SlotRequest();
                    newRequest.setSlotDate(currentDate);
                    newRequest.setStartTime(slotStart);
                    newRequest.setEndTime(slotEnd);

                    Slot slot = slotMapper.toSlot(newRequest);
                    slot.setSlotDate(currentDate);
                    slot.setRoom(room);
                    slot.setStaff(staffList);
                    slot.setSlotStatus(SlotStatus.AVAILABLE);
                    createdSlots.add(slotRepository.save(slot));
                }

                // Tăng thời gian tiếp theo
                slotStart = slotStart.plusMinutes(30);
                slotEnd = slotStart.plusMinutes(30);
            }

            currentDate = currentDate.plusDays(1);
        }

        return slotMapper.toSlotResponses(createdSlots);
    }



    @Transactional
    public void addMoreStaffToSlot(long slotId, long staffId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.SLOT_NOT_EXISTS));
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.STAFF_NOT_EXISTED));
        if (slot.getStaff() == null) {
            slot.setStaff(new ArrayList<>()); // hoặc ArrayList nếu bạn dùng List
        }

        if (slot.getStaff().contains(staff)) {
            throw new RuntimeException("Staff này đã trong slot");

        }
        slot.getStaff().add(staff);

    }


    public List<SlotInfoDTO> getAllUpcomingSlotsForUser(long locationId) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.LOCATION_NOT_EXISTS));

        // ✅ Lấy 2 ngày hợp lệ sắp tới (bỏ T7/CN)
        List<LocalDate> validDates = getNextWeekdays(today.plusDays(1), 5);
        if (validDates.size() < 2) return Collections.emptyList();

        List<SlotInfoDTO> result = new ArrayList<>();

        for (Room room : location.getRooms()) {
            for (Slot slot : room.getSlots()) {
                if (slot.getSlotStatus() == SlotStatus.AVAILABLE &&
                        validDates.contains(slot.getSlotDate())) {
                    SlotInfoDTO slotInfoDTO = slotMapper.toSlotInfoDto(slot);
                    result.add(slotInfoDTO);
                }
            }
        }

        return result;
    }


    private List<LocalDate> getNextWeekdays(LocalDate fromDate, int count) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate current = fromDate;

        while (result.size() < count) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                result.add(current);
            }
            current = current.plusDays(1);
        }

        return result;
    }


    public List<GetFullSlotResponse> getAllSlot() {
        List<GetFullSlotResponse> fullSlotResponses = new ArrayList<>();
        List<Slot> slotList = slotRepository.findAll();
        GetFullSlotResponse getAllServiceResponse = null;

        for (Slot slot : slotList) {
            SlotResponse slotResponse = slotMapper.toSlotResponse(slot);

            //lay room
            RoomSlotResponse roomSlotResponse = new RoomSlotResponse();
            roomSlotResponse.setRoomId(slot.getRoom().getRoomId());
            roomSlotResponse.setRoomName(slot.getRoom().getRoomName());
            roomSlotResponse.setOpenTime(slot.getRoom().getOpenTime());
            roomSlotResponse.setCloseTime(slot.getRoom().getCloseTime());

            List<StaffSlotResponse> staffSlotResponses = new ArrayList<>();
            for (Staff staff : slot.getStaff()) {
                StaffSlotResponse staffSlotResponse = new StaffSlotResponse();
                staffSlotResponse.setStaffId(staff.getStaffId());
                staffSlotResponse.setFullName(staff.getFullName());
                staffSlotResponses.add(staffSlotResponse);
            }
            GetFullSlotResponse getFullSlotResponse = new GetFullSlotResponse();
            getFullSlotResponse.setSlotResponse(slotResponse);
            getFullSlotResponse.setStaffSlotResponses(staffSlotResponses);
            getFullSlotResponse.setRoomSlotResponse(roomSlotResponse);


            //lay full response
            fullSlotResponses.add(getFullSlotResponse);


        }
        return fullSlotResponses;
    }

    // collector and staff get
    public List<GetFullSlotResponse> getAllSlotStaff(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = jwt.getClaim("id");

        Staff staffCheck = staffRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.STAFF_NOT_EXISTED));
        if (!List.of("STAFF", "SAMPLE_COLLECTOR").contains(staffCheck.getRole())) {
            throw new RuntimeException("Bạn không được phân công slot");
        }
        List<GetFullSlotResponse> fullSlotResponses = new ArrayList<>();
        List<Slot> slotList = slotRepository.findAll();
        GetFullSlotResponse getAllServiceResponse = null;

        for (Slot slot : slotList) {

            SlotResponse slotResponse = slotMapper.toSlotResponse(slot);

            //lay room
            RoomSlotResponse roomSlotResponse = new RoomSlotResponse();
            roomSlotResponse.setRoomId(slot.getRoom().getRoomId());
            roomSlotResponse.setRoomName(slot.getRoom().getRoomName());
            roomSlotResponse.setOpenTime(slot.getRoom().getOpenTime());
            roomSlotResponse.setCloseTime(slot.getRoom().getCloseTime());

            List<StaffSlotResponse> staffSlotResponses = new ArrayList<>();
            for (Staff staff : slot.getStaff()) {
                StaffSlotResponse staffSlotResponse = new StaffSlotResponse();
                staffSlotResponse.setStaffId(staff.getStaffId());
                staffSlotResponse.setFullName(staff.getFullName());
                staffSlotResponses.add(staffSlotResponse);
            }
            GetFullSlotResponse getFullSlotResponse = new GetFullSlotResponse();
            getFullSlotResponse.setSlotResponse(slotResponse);
            getFullSlotResponse.setStaffSlotResponses(staffSlotResponses);
            getFullSlotResponse.setRoomSlotResponse(roomSlotResponse);


            //lay full response
            fullSlotResponses.add(getFullSlotResponse);
        }
        return fullSlotResponses;
    }

    @Transactional
    public List<GetFullSlotResponse> getAllSlotOfStaff(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = jwt.getClaim("id");

        Staff staff = staffRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.STAFF_NOT_EXISTED));

        List<GetFullSlotResponse> fullSlotResponses = new ArrayList<>();

        for (Slot slot : staff.getSlots()) {
            if (slot.getSlotStatus().equals(SlotStatus.BOOKED)) {
                SlotResponse slotResponse = slotMapper.toSlotResponse(slot);
                RoomSlotResponse roomSlotResponse = new RoomSlotResponse();
                roomSlotResponse.setRoomId(slot.getRoom().getRoomId());
                roomSlotResponse.setRoomName(slot.getRoom().getRoomName());
                roomSlotResponse.setOpenTime(slot.getRoom().getOpenTime());
                roomSlotResponse.setCloseTime(slot.getRoom().getCloseTime());

                GetFullSlotResponse getFullSlotResponse = new GetFullSlotResponse();
                getFullSlotResponse.setSlotResponse(slotResponse);
                getFullSlotResponse.setRoomSlotResponse(roomSlotResponse);

                fullSlotResponses.add(getFullSlotResponse);
            }
        }

        return fullSlotResponses;
    }


    public void deleteSlot(long slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.SLOT_NOT_EXISTS));
        slotRepository.delete(slot);
    }

    @Transactional
    public void updateStaffToSlot(long staffId1, long staffId2, long slotId) {
        Staff staff1 = staffRepository.findById(staffId1)
                .orElseThrow(() -> new AppException(ErrorCodeUser.STAFF_NOT_EXISTED));
        Staff staff2 = staffRepository.findById(staffId2)
                .orElseThrow(() -> new AppException(ErrorCodeUser.STAFF_NOT_EXISTED));
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.SLOT_NOT_EXISTS));
        if (!staff1.getRole().equals(staff2.getRole())) {
            throw new RuntimeException("hai người này không trùng chức vụ");
        }
        List<Staff> staffList = slot.getStaff();
        for (int i = 0; i < staffList.size(); i++) {
            if (staffList.get(i) == staff1) {
                staffList.set(i, staff2);
                break;
            }
        }
    }

    @Transactional
    public SlotResponse updateSlot(SlotRequest slotRequest,
                                   long slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new AppException(ErrorCodeUser.SLOT_NOT_EXISTS));
        slot.setSlotDate(slotRequest.getSlotDate());
        slot.setStartTime(slotRequest.getStartTime());
        slot.setEndTime(slotRequest.getEndTime());
        SlotResponse slotResponse = slotMapper.toSlotResponse(slot);
        return slotResponse;
    }


}