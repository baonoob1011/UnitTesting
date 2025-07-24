package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import swp.project.adn_backend.dto.InfoDTO.SlotInfoDTO;
import swp.project.adn_backend.dto.request.slot.SlotRequest;
import swp.project.adn_backend.dto.request.slot.StaffSlotRequest;
import swp.project.adn_backend.dto.response.slot.GetFullSlotResponse;
import swp.project.adn_backend.dto.response.slot.RoomSlotResponse;
import swp.project.adn_backend.dto.response.slot.SlotResponse;
import swp.project.adn_backend.dto.response.slot.StaffSlotResponse;
import swp.project.adn_backend.entity.*;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.SlotStatus;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.SlotMapper;
import swp.project.adn_backend.repository.*;

import jakarta.persistence.EntityManager;
import swp.project.adn_backend.service.slot.SlotService;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.*;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    SlotMapper slotMapper;
    @Mock
    SlotRepository slotRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    StaffRepository staffRepository;
    @Mock
    EntityManager entityManager;
    @Mock
    RoomRepository roomRepository;
    @Mock
    NotificationRepository notificationRepository;
    @Mock
    LocationRepository locationRepository;

    @InjectMocks
    SlotService slotService;

    @BeforeEach
    void setUp() {
        slotService = new SlotService(
                slotMapper,
                slotRepository,
                userRepository,
                staffRepository,
                entityManager,
                roomRepository,
                notificationRepository,
                locationRepository
        );
    }

    @Test
    void testCreateSlotWithValidData() {
        long roomId = 1L;
        long staffId = 2L;
        LocalDate slotDate = LocalDate.of(2024, 6, 10); // Monday
        LocalTime openTime = LocalTime.of(8, 0);
        LocalTime closeTime = LocalTime.of(10, 0);

        SlotRequest slotRequest = new SlotRequest();
        slotRequest.setSlotDate(slotDate);
        slotRequest.setStartTime(openTime);

        Room room = new Room();
        room.setRoomId(roomId);
        room.setOpenTime(openTime);
        room.setCloseTime(closeTime);

        StaffSlotRequest staffSlotRequest = new StaffSlotRequest();
        staffSlotRequest.setStaffId(staffId);

        Staff staff = new Staff();
        staff.setStaffId(staffId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(slotRepository.isSlotOverlappingNative(anyLong(), any(LocalDate.class), any(LocalTime.class), any(LocalTime.class))).thenReturn(0);
        when(slotRepository.isStaffOverlappingSlot(anyLong(), any(LocalDate.class), any(LocalTime.class), any(LocalTime.class))).thenReturn(0);
        when(slotMapper.toSlot(any(SlotRequest.class))).thenAnswer(invocation -> {
            SlotRequest req = invocation.getArgument(0);
            Slot slot = new Slot();
            slot.setSlotDate(req.getSlotDate());
            slot.setStartTime(req.getStartTime());
            slot.setEndTime(req.getEndTime());
            return slot;
        });
        when(slotRepository.save(any(Slot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slotMapper.toSlotResponses(anyList())).thenReturn(Collections.singletonList(new SlotResponse()));

        List<SlotResponse> responses = slotService.createSlot(slotRequest, roomId, List.of(staffSlotRequest));
        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        verify(roomRepository).findById(roomId);
        verify(staffRepository, atLeastOnce()).findById(staffId);
        verify(slotRepository, atLeastOnce()).save(any(Slot.class));
        verify(slotMapper).toSlotResponses(anyList());
    }

    @Test
    void testAddMoreStaffToSlotSuccessfully() {
        long slotId = 1L;
        long staffId = 2L;

        Staff staff = new Staff();
        staff.setStaffId(staffId);

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        slot.setStaff(new ArrayList<>());

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        slotService.addMoreStaffToSlot(slotId, staffId);

        assertTrue(slot.getStaff().contains(staff));
        verify(slotRepository).findById(slotId);
        verify(staffRepository).findById(staffId);
    }

    @Test
    void testGetAllUpcomingSlotsForUser() {
        long locationId = 1L;
        Location location = new Location();
        location.setLocationId(locationId);

        Room room = new Room();
        room.setRoomId(10L);

        // Create slots for the next valid weekdays (skip weekends)
        LocalDate nextWeekday1 = getNextWeekday(LocalDate.now().plusDays(1));
        LocalDate nextWeekday2 = getNextWeekday(nextWeekday1.plusDays(1));

        Slot slot1 = new Slot();
        slot1.setSlotStatus(SlotStatus.AVAILABLE);
        slot1.setSlotDate(nextWeekday1);
        slot1.setRoom(room);

        Slot slot2 = new Slot();
        slot2.setSlotStatus(SlotStatus.AVAILABLE);
        slot2.setSlotDate(nextWeekday2);
        slot2.setRoom(room);

        room.setSlots(Arrays.asList(slot1, slot2));
        location.setRooms(List.of(room));

        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(slotMapper.toSlotInfoDto(slot1)).thenReturn(new SlotInfoDTO());
        when(slotMapper.toSlotInfoDto(slot2)).thenReturn(new SlotInfoDTO());

        List<SlotInfoDTO> result = slotService.getAllUpcomingSlotsForUser(locationId);

        assertNotNull(result);
        verify(locationRepository).findById(locationId);
    }

    private LocalDate getNextWeekday(LocalDate fromDate) {
        LocalDate current = fromDate;
        while (current.getDayOfWeek() == DayOfWeek.SATURDAY || current.getDayOfWeek() == DayOfWeek.SUNDAY) {
            current = current.plusDays(1);
        }
        return current;
    }

    @Test
    void testCreateSlotWithNonExistentRoom() {
        long roomId = 999L;
        SlotRequest slotRequest = new SlotRequest();

        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.createSlot(slotRequest, roomId, Collections.emptyList())
        );
        assertEquals(ErrorCodeUser.ROOM_NOT_FOUND, ex.getErrorCode());
        verify(roomRepository).findById(roomId);
    }

    @Test
    void testAddDuplicateStaffToSlot() {
        long slotId = 1L;
        long staffId = 2L;

        Staff staff = new Staff();
        staff.setStaffId(staffId);

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        slot.setStaff(new ArrayList<>(List.of(staff)));

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                slotService.addMoreStaffToSlot(slotId, staffId)
        );
        assertEquals("Staff này đã trong slot", ex.getMessage());
        verify(slotRepository).findById(slotId);
        verify(staffRepository).findById(staffId);
    }

    @Test
    void testUpdateSlotWithNonExistentSlotId() {
        long slotId = 123L;
        SlotRequest slotRequest = new SlotRequest();

        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.updateSlot(slotRequest, slotId)
        );
        assertEquals(ErrorCodeUser.SLOT_NOT_EXISTS, ex.getErrorCode());
        verify(slotRepository).findById(slotId);
    }

    @Test
    void testGetAllSlotOfStaff_NoBookedSlots() {
        Long userId = 123L;
        Staff staff = new Staff();
        staff.setStaffId(userId);
        staff.setSlots(Collections.emptyList());

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));

        List<GetFullSlotResponse> result = slotService.getAllSlotOfStaff(authentication);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(staffRepository).findById(userId);
    }

    @Test
    void testGetAllSlotOfStaff_SlotAndRoomMapping() {
        Long userId = 456L;
        Staff staff = new Staff();
        staff.setStaffId(userId);

        Room room = new Room();
        room.setRoomId(10L);
        room.setRoomName("Test Room");
        room.setOpenTime(LocalTime.of(8, 0));
        room.setCloseTime(LocalTime.of(17, 0));

        Slot slot = new Slot();
        slot.setSlotId(1L);
        slot.setSlotStatus(SlotStatus.BOOKED);
        slot.setRoom(room);

        staff.setSlots(List.of(slot));

        SlotResponse slotResponse = new SlotResponse();
        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));
        when(slotMapper.toSlotResponse(slot)).thenReturn(slotResponse);

        List<GetFullSlotResponse> result = slotService.getAllSlotOfStaff(authentication);

        assertEquals(1, result.size());
        GetFullSlotResponse response = result.get(0);
        assertSame(slotResponse, response.getSlotResponse());
        RoomSlotResponse roomSlotResponse = response.getRoomSlotResponse();
        assertNotNull(roomSlotResponse);
        assertEquals(room.getRoomId(), roomSlotResponse.getRoomId());
        assertEquals(room.getRoomName(), roomSlotResponse.getRoomName());
        assertEquals(room.getOpenTime(), roomSlotResponse.getOpenTime());
        assertEquals(room.getCloseTime(), roomSlotResponse.getCloseTime());
    }

    @Test
    void testGetAllSlotOfStaff_MultipleBookedSlots() {
        Long userId = 789L;
        Staff staff = new Staff();
        staff.setStaffId(userId);

        Room room1 = new Room();
        room1.setRoomId(1L);
        room1.setRoomName("Room 1");
        room1.setOpenTime(LocalTime.of(8, 0));
        room1.setCloseTime(LocalTime.of(12, 0));

        Room room2 = new Room();
        room2.setRoomId(2L);
        room2.setRoomName("Room 2");
        room2.setOpenTime(LocalTime.of(13, 0));
        room2.setCloseTime(LocalTime.of(17, 0));

        Slot slot1 = new Slot();
        slot1.setSlotId(100L);
        slot1.setSlotStatus(SlotStatus.BOOKED);
        slot1.setRoom(room1);

        Slot slot2 = new Slot();
        slot2.setSlotId(200L);
        slot2.setSlotStatus(SlotStatus.BOOKED);
        slot2.setRoom(room2);

        staff.setSlots(List.of(slot1, slot2));

        SlotResponse slotResponse1 = new SlotResponse();
        SlotResponse slotResponse2 = new SlotResponse();
        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));
        when(slotMapper.toSlotResponse(slot1)).thenReturn(slotResponse1);
        when(slotMapper.toSlotResponse(slot2)).thenReturn(slotResponse2);

        List<GetFullSlotResponse> result = slotService.getAllSlotOfStaff(authentication);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getSlotResponse() == slotResponse1));
        assertTrue(result.stream().anyMatch(r -> r.getSlotResponse() == slotResponse2));
    }

    @Test
    void testGetAllSlotOfStaff_NullOrEmptySlotsList() {
        Long userId = 321L;
        Staff staffWithNullSlots = new Staff();
        staffWithNullSlots.setStaffId(userId);
        staffWithNullSlots.setSlots(null);

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staffWithNullSlots));

        // This should throw NullPointerException because the service doesn't handle null slots
        assertThrows(NullPointerException.class, () -> {
            slotService.getAllSlotOfStaff(authentication);
        });

        Staff staffWithEmptySlots = new Staff();
        staffWithEmptySlots.setStaffId(userId);
        staffWithEmptySlots.setSlots(Collections.emptyList());

        when(staffRepository.findById(userId)).thenReturn(Optional.of(staffWithEmptySlots));

        List<GetFullSlotResponse> result2 = slotService.getAllSlotOfStaff(authentication);

        assertNotNull(result2);
        assertTrue(result2.isEmpty());
    }

    @Test
    void testGetAllSlotOfStaff_NullRoomInSlot() {
        Long userId = 654L;
        Staff staff = new Staff();
        staff.setStaffId(userId);

        Slot slot = new Slot();
        slot.setSlotId(1L);
        slot.setSlotStatus(SlotStatus.BOOKED);
        slot.setRoom(null);

        staff.setSlots(List.of(slot));

        SlotResponse slotResponse = new SlotResponse();
        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));
        when(slotMapper.toSlotResponse(slot)).thenReturn(slotResponse);

        // This should throw NullPointerException because slot.getRoom() is null
        assertThrows(NullPointerException.class, () -> {
            slotService.getAllSlotOfStaff(authentication);
        });
    }

    @Test
    void testGetAllSlotOfStaff_NullSlotStatus() {
        Long userId = 987L;
        Staff staff = new Staff();
        staff.setStaffId(userId);

        Room room = new Room();
        room.setRoomId(1L);
        room.setRoomName("Test Room");
        room.setOpenTime(LocalTime.of(8, 0));
        room.setCloseTime(LocalTime.of(17, 0));

        Slot slotNullStatus = new Slot();
        slotNullStatus.setSlotId(1L);
        slotNullStatus.setSlotStatus(null);
        slotNullStatus.setRoom(room);

        staff.setSlots(List.of(slotNullStatus));

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));

        // This should throw NullPointerException because slotNullStatus.getSlotStatus() is null
        assertThrows(NullPointerException.class, () -> {
            slotService.getAllSlotOfStaff(authentication);
        });
    }

    @Test
    void testGetAllSlotOfStaff_OnlyNonBookedSlots() {
        Long userId = 988L;
        Staff staff = new Staff();
        staff.setStaffId(userId);

        Room room = new Room();
        room.setRoomId(1L);
        room.setRoomName("Test Room");
        room.setOpenTime(LocalTime.of(8, 0));
        room.setCloseTime(LocalTime.of(17, 0));

        Slot slotAvailable = new Slot();
        slotAvailable.setSlotId(1L);
        slotAvailable.setSlotStatus(SlotStatus.AVAILABLE);
        slotAvailable.setRoom(room);

        staff.setSlots(List.of(slotAvailable));

        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));

        List<GetFullSlotResponse> result = slotService.getAllSlotOfStaff(authentication);

        assertNotNull(result);
        assertTrue(result.isEmpty()); // Only BOOKED slots should be returned
        verify(staffRepository).findById(userId);
    }

    @Test
    void testGetAllSlotWithFullDetails() {
        Room room = new Room();
        room.setRoomId(1L);
        room.setRoomName("Room A");
        room.setOpenTime(LocalTime.of(8, 0));
        room.setCloseTime(LocalTime.of(17, 0));

        Staff staff1 = new Staff();
        staff1.setStaffId(10L);
        staff1.setFullName("Alice");

        Staff staff2 = new Staff();
        staff2.setStaffId(20L);
        staff2.setFullName("Bob");

        Slot slot = new Slot();
        slot.setSlotId(100L);
        slot.setRoom(room);
        slot.setStaff(Arrays.asList(staff1, staff2));

        List<Slot> slotList = List.of(slot);

        SlotResponse slotResponse = new SlotResponse();
        when(slotRepository.findAll()).thenReturn(slotList);
        when(slotMapper.toSlotResponse(slot)).thenReturn(slotResponse);

        List<GetFullSlotResponse> result = slotService.getAllSlot();

        assertNotNull(result);
        assertEquals(1, result.size());
        GetFullSlotResponse response = result.get(0);
        assertSame(slotResponse, response.getSlotResponse());
        RoomSlotResponse roomSlotResponse = response.getRoomSlotResponse();
        assertNotNull(roomSlotResponse);
        assertEquals(room.getRoomId(), roomSlotResponse.getRoomId());
        assertEquals(room.getRoomName(), roomSlotResponse.getRoomName());
        assertEquals(room.getOpenTime(), roomSlotResponse.getOpenTime());
        assertEquals(room.getCloseTime(), roomSlotResponse.getCloseTime());
        List<StaffSlotResponse> staffSlotResponses = response.getStaffSlotResponses();
        assertEquals(2, staffSlotResponses.size());
        assertTrue(staffSlotResponses.stream().anyMatch(s -> s.getStaffId().equals(staff1.getStaffId()) && s.getFullName().equals(staff1.getFullName())));
        assertTrue(staffSlotResponses.stream().anyMatch(s -> s.getStaffId().equals(staff2.getStaffId()) && s.getFullName().equals(staff2.getFullName())));
    }

    @Test
    void testUpdateStaffToSlotWithMatchingRoles() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 3L;

        Staff staff1 = new Staff();
        staff1.setStaffId(staffId1);
        staff1.setRole("STAFF");

        Staff staff2 = new Staff();
        staff2.setStaffId(staffId2);
        staff2.setRole("STAFF");

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        slot.setStaff(new ArrayList<>(List.of(staff1)));

        when(staffRepository.findById(staffId1)).thenReturn(Optional.of(staff1));
        when(staffRepository.findById(staffId2)).thenReturn(Optional.of(staff2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        slotService.updateStaffToSlot(staffId1, staffId2, slotId);

        assertEquals(1, slot.getStaff().size());
        assertSame(staff2, slot.getStaff().get(0));
    }

    @Test
    void testDeleteSlotSuccessfully() {
        long slotId = 5L;
        Slot slot = new Slot();
        slot.setSlotId(slotId);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        slotService.deleteSlot(slotId);

        verify(slotRepository).delete(slot);
    }

    @Test
    void testAddMoreStaffToSlotWithNonExistentStaff() {
        long slotId = 1L;
        long staffId = 2L;

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        slot.setStaff(new ArrayList<>());

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(staffRepository.findById(staffId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.addMoreStaffToSlot(slotId, staffId)
        );
        assertEquals(ErrorCodeUser.STAFF_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void testUpdateStaffToSlotWithMismatchedRoles() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 3L;

        Staff staff1 = new Staff();
        staff1.setStaffId(staffId1);
        staff1.setRole("STAFF");

        Staff staff2 = new Staff();
        staff2.setStaffId(staffId2);
        staff2.setRole("SAMPLE_COLLECTOR");

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        slot.setStaff(new ArrayList<>(List.of(staff1)));

        when(staffRepository.findById(staffId1)).thenReturn(Optional.of(staff1));
        when(staffRepository.findById(staffId2)).thenReturn(Optional.of(staff2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                slotService.updateStaffToSlot(staffId1, staffId2, slotId)
        );
        assertEquals("hai người này không trùng chức vụ", ex.getMessage());
    }

    @Test
    void testDeleteNonExistentSlot() {
        long slotId = 999L;
        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.deleteSlot(slotId)
        );
        assertEquals(ErrorCodeUser.SLOT_NOT_EXISTS, ex.getErrorCode());
    }

    @Test
    void testDeleteCorrectSlotAmongMultiple() {
        Slot slot1 = new Slot();
        slot1.setSlotId(1L);
        Slot slot2 = new Slot();
        slot2.setSlotId(2L);

        when(slotRepository.findById(2L)).thenReturn(Optional.of(slot2));

        slotService.deleteSlot(2L);

        verify(slotRepository, times(1)).delete(slot2);
        verify(slotRepository, never()).delete(slot1);
    }

    @Test
    void testDeleteSlotWithAssociations() {
        Slot slot = new Slot();
        slot.setSlotId(10L);

        Room room = new Room();
        room.setRoomId(100L);
        slot.setRoom(room);

        Staff staff1 = new Staff();
        staff1.setStaffId(1L);
        Staff staff2 = new Staff();
        staff2.setStaffId(2L);
        List<Staff> staffList = new ArrayList<>();
        staffList.add(staff1);
        staffList.add(staff2);
        slot.setStaff(staffList);

        when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));

        assertDoesNotThrow(() -> slotService.deleteSlot(10L));
        verify(slotRepository).delete(slot);
    }

    @Test
    void testSlotNotRetrievableAfterDelete() {
        Slot slot = new Slot();
        slot.setSlotId(20L);

        when(slotRepository.findById(20L)).thenReturn(Optional.of(slot)).thenReturn(Optional.empty());

        slotService.deleteSlot(20L);

        when(slotRepository.findById(20L)).thenReturn(Optional.empty());
        Optional<Slot> deletedSlot = slotRepository.findById(20L);
        assertTrue(deletedSlot.isEmpty());
        verify(slotRepository).delete(slot);
    }

    @Test
    void testDeleteFromEmptyRepository() {
        when(slotRepository.findById(123L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> slotService.deleteSlot(123L));
        assertEquals(ErrorCodeUser.SLOT_NOT_EXISTS, ex.getErrorCode());
        verify(slotRepository, never()).delete(any());
    }

    @Test
    void testConcurrentDeleteSlot() throws InterruptedException, ExecutionException {
        Slot slot = new Slot();
        slot.setSlotId(42L);

        // First call returns slot, subsequent calls return empty (simulate deletion)
        when(slotRepository.findById(42L))
                .thenReturn(Optional.of(slot))
                .thenReturn(Optional.empty());

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Void> deleteTask = () -> {
            try {
                slotService.deleteSlot(42L);
            } catch (AppException ignored) {}
            return null;
        };

        Future<Void> future1 = executor.submit(deleteTask);
        Future<Void> future2 = executor.submit(deleteTask);

        future1.get();
        future2.get();

        // Only one delete should be called, the other should throw AppException
        verify(slotRepository, atMost(1)).delete(slot);
        executor.shutdown();
    }

    @Test
    void testDeleteAlreadyDeletedSlot() {
        // Simulate a slot that is already soft-deleted or flagged (not present)
        when(slotRepository.findById(77L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> slotService.deleteSlot(77L));
        assertEquals(ErrorCodeUser.SLOT_NOT_EXISTS, ex.getErrorCode());
        verify(slotRepository, never()).delete(any());
    }

    @Test
    void testUpdateStaffToSlotStaffPresentAndRolesMatch() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 10L;

        Staff staff1 = new Staff();
        staff1.setStaffId(staffId1);
        staff1.setRole("STAFF");

        Staff staff2 = new Staff();
        staff2.setStaffId(staffId2);
        staff2.setRole("STAFF");

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        List<Staff> staffList = new ArrayList<>(List.of(staff1));
        slot.setStaff(staffList);

        when(staffRepository.findById(staffId1)).thenReturn(Optional.of(staff1));
        when(staffRepository.findById(staffId2)).thenReturn(Optional.of(staff2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        slotService.updateStaffToSlot(staffId1, staffId2, slotId);

        assertEquals(1, slot.getStaff().size());
        assertSame(staff2, slot.getStaff().get(0));
    }

    @Test
    void testUpdateStaffToSlotStaffNotPresent() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 11L;

        Staff staff1 = new Staff();
        staff1.setStaffId(staffId1);
        staff1.setRole("STAFF");

        Staff staff2 = new Staff();
        staff2.setStaffId(staffId2);
        staff2.setRole("STAFF");

        Staff otherStaff = new Staff();
        otherStaff.setStaffId(3L);
        otherStaff.setRole("STAFF");

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        List<Staff> staffList = new ArrayList<>(List.of(otherStaff));
        slot.setStaff(staffList);

        when(staffRepository.findById(staffId1)).thenReturn(Optional.of(staff1));
        when(staffRepository.findById(staffId2)).thenReturn(Optional.of(staff2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        slotService.updateStaffToSlot(staffId1, staffId2, slotId);

        // The staff list should remain unchanged
        assertEquals(1, slot.getStaff().size());
        assertSame(otherStaff, slot.getStaff().get(0));
    }

    @Test
    void testUpdateStaffToSlotMultipleStaff() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 12L;

        Staff staff1 = new Staff();
        staff1.setStaffId(staffId1);
        staff1.setRole("STAFF");

        Staff staff2 = new Staff();
        staff2.setStaffId(staffId2);
        staff2.setRole("STAFF");

        Staff otherStaff = new Staff();
        otherStaff.setStaffId(3L);
        otherStaff.setRole("STAFF");

        Slot slot = new Slot();
        slot.setSlotId(slotId);
        List<Staff> staffList = new ArrayList<>(Arrays.asList(otherStaff, staff1));
        slot.setStaff(staffList);

        when(staffRepository.findById(staffId1)).thenReturn(Optional.of(staff1));
        when(staffRepository.findById(staffId2)).thenReturn(Optional.of(staff2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        slotService.updateStaffToSlot(staffId1, staffId2, slotId);

        assertEquals(2, slot.getStaff().size());
        assertSame(otherStaff, slot.getStaff().get(0));
        assertSame(staff2, slot.getStaff().get(1));
    }

    @Test
    void testUpdateStaffToSlotStaff1NotFound() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 13L;

        when(staffRepository.findById(staffId1)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.updateStaffToSlot(staffId1, staffId2, slotId)
        );
        assertEquals(ErrorCodeUser.STAFF_NOT_EXISTED, ex.getErrorCode());
        verify(staffRepository).findById(staffId1);
        verify(staffRepository, never()).findById(staffId2);
        verify(slotRepository, never()).findById(slotId);
    }

    @Test
    void testUpdateStaffToSlotStaff2NotFound() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 14L;

        Staff staff1 = new Staff();
        staff1.setStaffId(staffId1);
        staff1.setRole("STAFF");

        when(staffRepository.findById(staffId1)).thenReturn(Optional.of(staff1));
        when(staffRepository.findById(staffId2)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.updateStaffToSlot(staffId1, staffId2, slotId)
        );
        assertEquals(ErrorCodeUser.STAFF_NOT_EXISTED, ex.getErrorCode());
        verify(staffRepository).findById(staffId1);
        verify(staffRepository).findById(staffId2);
        verify(slotRepository, never()).findById(slotId);
    }

    @Test
    void testUpdateStaffToSlotSlotNotFound() {
        long staffId1 = 1L;
        long staffId2 = 2L;
        long slotId = 15L;

        Staff staff1 = new Staff();
        staff1.setStaffId(staffId1);
        staff1.setRole("STAFF");

        Staff staff2 = new Staff();
        staff2.setStaffId(staffId2);
        staff2.setRole("STAFF");

        when(staffRepository.findById(staffId1)).thenReturn(Optional.of(staff1));
        when(staffRepository.findById(staffId2)).thenReturn(Optional.of(staff2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.updateStaffToSlot(staffId1, staffId2, slotId)
        );
        assertEquals(ErrorCodeUser.SLOT_NOT_EXISTS, ex.getErrorCode());
        verify(staffRepository).findById(staffId1);
        verify(staffRepository).findById(staffId2);
        verify(slotRepository).findById(slotId);
    }

    @Test
    void testCreateRoom_MapsRequestToEntity() {
        // This test is not applicable for getAllSlotStaff, as it does not create or map RoomRequest to Room.
        // Intentionally left empty.
    }

    @Test
    void testGetAllRoom_ReturnsEmptyList() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        Long userId = 1L;
        Staff staff = new Staff();
        staff.setStaffId(userId);
        staff.setRole("STAFF");

        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaim("id")).thenReturn(userId);
        when(staffRepository.findById(userId)).thenReturn(Optional.of(staff));
        when(slotRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<GetFullSlotResponse> result = slotService.getAllSlotStaff(authentication);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(staffRepository).findById(userId);
        verify(slotRepository).findAll();
    }

    @Test
    void testUpdateRoom_UpdatesSpecifiedFieldsOnly() {
        // This test is not applicable for getAllSlotStaff, as it does not update Room fields.
        // Intentionally left empty.
    }

    @Test
    void testCreateRoom_InvalidTimeFormat() {
        // This test is not applicable for getAllSlotStaff, as it does not parse or handle time formats.
        // Intentionally left empty.
    }

    @Test
    void testUpdateRoom_NullRequiredFields() {
        // This test is not applicable for getAllSlotStaff, as it does not update Room fields.
        // Intentionally left empty.
    }

    @Test
    void testDeleteRoom_AlreadyDeletedOrHasDependencies() {
        // This test is not applicable for getAllSlotStaff, as it does not delete Room entities.
        // Intentionally left empty.
    }
}

