package swp.project.adn_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import swp.project.adn_backend.dto.InfoDTO.RoomInfoDTO;
import swp.project.adn_backend.dto.request.slot.RoomRequest;
import swp.project.adn_backend.entity.Location;
import swp.project.adn_backend.entity.Room;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.enums.RoomStatus;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.mapper.RoomMapper;
import swp.project.adn_backend.repository.LocationRepository;
import swp.project.adn_backend.repository.RoomRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import swp.project.adn_backend.service.room.RoomService;

import java.sql.Time;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {


    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private TypedQuery<RoomInfoDTO> typedQuery;

    @InjectMocks
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomMapper, entityManager, locationRepository);
    }

    @Test
    void testCreateRoomWithValidInput() {
        long locationId = 1L;
        RoomRequest roomRequest = new RoomRequest(0L, "Room A", LocalTime.of(8, 0), LocalTime.of(17, 0), RoomStatus.AVAILABLE);
        Location location = new Location();
        Room mappedRoom = new Room();
        Room savedRoom = new Room();

        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(roomMapper.toRoom(roomRequest)).thenReturn(mappedRoom);
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);

        Room result = roomService.createRoom(roomRequest, locationId);

        assertEquals(savedRoom, result);
        assertEquals(RoomStatus.AVAILABLE, mappedRoom.getRoomStatus());
        assertEquals(location, mappedRoom.getLocation());
        verify(roomRepository).save(mappedRoom);
    }

    @Test
    void testGetAllRoomReturnsAvailableRooms() {
        List<RoomInfoDTO> expectedRooms = Arrays.asList(
                new RoomInfoDTO(1L, "Room A", RoomStatus.AVAILABLE, LocalTime.of(8, 0), LocalTime.of(17, 0)),
                new RoomInfoDTO(2L, "Room B", RoomStatus.AVAILABLE, LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        when(entityManager.createQuery(anyString(), eq(RoomInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("roomStatus"), eq(RoomStatus.AVAILABLE))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(expectedRooms);

        List<RoomInfoDTO> result = roomService.getAllRoom();

        assertEquals(expectedRooms, result);
        verify(typedQuery).setParameter("roomStatus", RoomStatus.AVAILABLE);
        verify(typedQuery).getResultList();
    }

    @Test
    void testUpdateRoomWithValidTimeRange() {
        Long roomId = 1L;
        RoomRequest roomRequest = new RoomRequest(roomId, "Updated Room", LocalTime.of(10, 0), LocalTime.of(18, 0), RoomStatus.AVAILABLE);
        Room existingRoom = new Room();
        existingRoom.setRoomId(roomId);
        existingRoom.setRoomName("Old Room");
        existingRoom.setOpenTime(LocalTime.of(8, 0));
        existingRoom.setCloseTime(LocalTime.of(17, 0));

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.isRoomTimeOverlapping(eq(roomId), any(Time.class), any(Time.class))).thenReturn(0);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room updatedRoom = roomService.updateRoom(roomId, roomRequest);

        assertEquals("Updated Room", updatedRoom.getRoomName());
        assertEquals(LocalTime.of(10, 0), updatedRoom.getOpenTime());
        assertEquals(LocalTime.of(18, 0), updatedRoom.getCloseTime());
        verify(roomRepository).save(existingRoom);
    }

    @Test
    void testCreateRoomWithNonExistentLocationThrowsException() {
        long locationId = 99L;
        RoomRequest roomRequest = new RoomRequest(0L, "Room X", LocalTime.of(8, 0), LocalTime.of(17, 0), RoomStatus.AVAILABLE);

        when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            roomService.createRoom(roomRequest, locationId);
        });

        assertEquals(ErrorCodeUser.LOCATION_NOT_EXISTS, exception.getErrorCode());
    }

    @Test
    void testUpdateRoomWithOverlappingTimeThrowsException() {
        Long roomId = 2L;
        RoomRequest roomRequest = new RoomRequest(roomId, "Room Overlap", LocalTime.of(9, 0), LocalTime.of(15, 0), RoomStatus.AVAILABLE);
        Room existingRoom = new Room();
        existingRoom.setRoomId(roomId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.isRoomTimeOverlapping(eq(roomId), any(Time.class), any(Time.class))).thenReturn(1);

        AppException exception = assertThrows(AppException.class, () -> {
            roomService.updateRoom(roomId, roomRequest);
        });

        assertEquals(ErrorCodeUser.ROOM_TIME_OVERLAP, exception.getErrorCode());
    }

    @Test
    void testDeleteNonExistentRoomThrowsException() {
        Long roomId = 123L;
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            roomService.deleteRoom(roomId);
        });

        assertEquals(ErrorCodeUser.ROOM_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testDeleteRoomSuccessfully() {
        Long roomId = 1L;
        Room existingRoom = new Room();
        existingRoom.setRoomId(roomId);
        existingRoom.setRoomName("Room to Delete");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));

        assertDoesNotThrow(() -> roomService.deleteRoom(roomId));

        verify(roomRepository).findById(roomId);
        verify(roomRepository).delete(existingRoom);
    }

    @Test
    void testGetAllRoomByLocationIdReturnsFilteredRooms() {
        long locationId = 1L;
        List<RoomInfoDTO> expectedRooms = Arrays.asList(
                new RoomInfoDTO(1L, "Room A", RoomStatus.AVAILABLE, LocalTime.of(8, 0), LocalTime.of(17, 0)),
                new RoomInfoDTO(2L, "Room B", RoomStatus.AVAILABLE, LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        when(entityManager.createQuery(anyString(), eq(RoomInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("roomStatus"), eq(RoomStatus.AVAILABLE))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("locationId"), eq(locationId))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(expectedRooms);

        List<RoomInfoDTO> result = roomService.getAllRoomByLocationId(locationId);

        assertEquals(expectedRooms, result);
        verify(typedQuery).setParameter("roomStatus", RoomStatus.AVAILABLE);
        verify(typedQuery).setParameter("locationId", locationId);
        verify(typedQuery).getResultList();
    }

    @Test
    void testGetAllRoomByLocationIdReturnsEmptyList() {
        long locationId = 999L;
        List<RoomInfoDTO> emptyList = new ArrayList<>();

        when(entityManager.createQuery(anyString(), eq(RoomInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("roomStatus"), eq(RoomStatus.AVAILABLE))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("locationId"), eq(locationId))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(emptyList);

        List<RoomInfoDTO> result = roomService.getAllRoomByLocationId(locationId);

        assertTrue(result.isEmpty());
        verify(typedQuery).setParameter("roomStatus", RoomStatus.AVAILABLE);
        verify(typedQuery).setParameter("locationId", locationId);
    }

    @Test
    void testGetAllRoomActiveReturnsActiveRooms() {
        List<RoomInfoDTO> expectedActiveRooms = Arrays.asList(
                new RoomInfoDTO(3L, "Active Room A", RoomStatus.ACTIVE, LocalTime.of(8, 0), LocalTime.of(17, 0)),
                new RoomInfoDTO(4L, "Active Room B", RoomStatus.ACTIVE, LocalTime.of(9, 0), LocalTime.of(18, 0))
        );

        when(entityManager.createQuery(anyString(), eq(RoomInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("roomStatus"), eq(RoomStatus.ACTIVE))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(expectedActiveRooms);

        List<RoomInfoDTO> result = roomService.getAllRoomActive();

        assertEquals(expectedActiveRooms, result);
        verify(typedQuery).setParameter("roomStatus", RoomStatus.ACTIVE);
        verify(typedQuery).getResultList();
    }

    @Test
    void testGetAllRoomActiveReturnsEmptyList() {
        List<RoomInfoDTO> emptyList = new ArrayList<>();

        when(entityManager.createQuery(anyString(), eq(RoomInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("roomStatus"), eq(RoomStatus.ACTIVE))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(emptyList);

        List<RoomInfoDTO> result = roomService.getAllRoomActive();

        assertTrue(result.isEmpty());
        verify(typedQuery).setParameter("roomStatus", RoomStatus.ACTIVE);
    }

    @Test
    void testUpdateRoomWithNonExistentRoomThrowsException() {
        Long roomId = 999L;
        RoomRequest roomRequest = new RoomRequest(roomId, "Non-existent Room", LocalTime.of(10, 0), LocalTime.of(18, 0), RoomStatus.AVAILABLE);

        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            roomService.updateRoom(roomId, roomRequest);
        });

        assertEquals(ErrorCodeUser.ROOM_NOT_FOUND, exception.getErrorCode());
        verify(roomRepository).findById(roomId);
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    void testUpdateRoomWithNullOverlapResult() {
        Long roomId = 1L;
        RoomRequest roomRequest = new RoomRequest(roomId, "Updated Room", LocalTime.of(10, 0), LocalTime.of(18, 0), RoomStatus.AVAILABLE);
        Room existingRoom = new Room();
        existingRoom.setRoomId(roomId);
        existingRoom.setRoomName("Old Room");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.isRoomTimeOverlapping(eq(roomId), any(Time.class), any(Time.class))).thenReturn(null);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room updatedRoom = roomService.updateRoom(roomId, roomRequest);

        assertEquals("Updated Room", updatedRoom.getRoomName());
        assertEquals(LocalTime.of(10, 0), updatedRoom.getOpenTime());
        assertEquals(LocalTime.of(18, 0), updatedRoom.getCloseTime());
        verify(roomRepository).save(existingRoom);
    }

    @Test
    void testGetAllRoomReturnsEmptyList() {
        List<RoomInfoDTO> emptyList = new ArrayList<>();

        when(entityManager.createQuery(anyString(), eq(RoomInfoDTO.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(eq("roomStatus"), eq(RoomStatus.AVAILABLE))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(emptyList);

        List<RoomInfoDTO> result = roomService.getAllRoom();

        assertTrue(result.isEmpty());
        verify(typedQuery).setParameter("roomStatus", RoomStatus.AVAILABLE);
    }
}
