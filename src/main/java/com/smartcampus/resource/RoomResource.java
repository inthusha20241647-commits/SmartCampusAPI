package com.smartcampus.resource;

import java.util.Map;
import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.Room;
import com.smartcampus.store.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("/rooms")
public class RoomResource {

    // GET /api/v1/rooms — return all rooms
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRooms() {
        List<Room> roomList = new ArrayList<>(DataStore.rooms.values());
        return Response.ok(roomList).build();
    }

    // POST /api/v1/rooms — create a new room
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createRoom(Room room) {
        if (room.getId() == null || room.getId().isEmpty()) {
            room.setId(UUID.randomUUID().toString());
        }

        if (room.getName() == null || room.getName().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createError("Room name is required"))
                    .build();
        }

        if (DataStore.rooms.containsKey(room.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(createError("Room with this ID already exists"))
                    .build();
        }

        DataStore.rooms.put(room.getId(), room);

        return Response.created(URI.create("/api/v1/rooms/" + room.getId()))
                .entity(room)
                .build();
    }

    // GET /api/v1/rooms/{roomId} — get a specific room
    @GET
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);

        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createError("Room not found with ID: " + roomId))
                    .build();
        }

        return Response.ok(room).build();
    }

    // DELETE /api/v1/rooms/{roomId} — delete a room
    @DELETE
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);

        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createError("Room not found with ID: " + roomId))
                    .build();
        }

        // Business logic — cannot delete room if it has sensors
        if (!room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException(
                    "Cannot delete room '" + roomId +
                            "' because it still has " + room.getSensorIds().size() +
                            " sensor(s) assigned to it."
            );
        }

        DataStore.rooms.remove(roomId);
        return Response.noContent().build(); // 204
    }

    // Helper method to create error response body
    private Map<String, String> createError(String message) {
        Map<String, String> error = new java.util.HashMap<>();
        error.put("error", message);
        return error;
    }
}