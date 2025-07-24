package swp.project.adn_backend.controller.slot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swp.project.adn_backend.dto.InfoDTO.RoomInfoDTO;
import swp.project.adn_backend.dto.request.slot.RoomRequest;
import swp.project.adn_backend.entity.Room;
import swp.project.adn_backend.service.room.RoomService;

import java.util.List;

@RestController
@RequestMapping("/api/room")
public class RoomController {
    @Autowired
    private RoomService roomService;

    @PostMapping("/create-room")
    public ResponseEntity<Room> createRoom(@RequestBody RoomRequest roomRequest,
                                           @RequestParam long locationId) {
        return ResponseEntity.ok(roomService.createRoom(roomRequest,locationId));
    }

    @GetMapping("/get-all-room-by-location-id")
    public ResponseEntity<List<RoomInfoDTO>> getAllRoomByLocationId(@RequestParam long locationId) {
        return ResponseEntity.ok(roomService.getAllRoomByLocationId(locationId));
    }
    @GetMapping("/get-all-room")
    public ResponseEntity<List<RoomInfoDTO>> getAllRoom() {
        return ResponseEntity.ok(roomService.getAllRoom());
    }


    @GetMapping("/get-all-room-active")
    public ResponseEntity<List<RoomInfoDTO>> getAllRoomActive() {
        return ResponseEntity.ok(roomService.getAllRoomActive());
    }

    @DeleteMapping("/delete-room/{id}")
    public ResponseEntity<String> deleteRoom(@PathVariable long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok("delete successfully");
    }

    @PutMapping("/update-room/{id}")
    public ResponseEntity<Room> updateRoom(
            @PathVariable("id") Long roomId,
            @RequestBody RoomRequest roomRequest) {
        Room updated = roomService.updateRoom(roomId, roomRequest);
        return ResponseEntity.ok(updated);
    }
}

