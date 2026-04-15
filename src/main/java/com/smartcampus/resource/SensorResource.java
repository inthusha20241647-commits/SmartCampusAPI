package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Sensor;
import com.smartcampus.store.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/sensors")
public class SensorResource {

    // GET /api/v1/sensors — get all sensors, optional filter by type
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSensors(@QueryParam("type") String type) {
        List<Sensor> sensorList = new ArrayList<>(DataStore.sensors.values());

        if (type != null && !type.isEmpty()) {
            List<Sensor> filtered = new ArrayList<>();
            for (Sensor s : sensorList) {
                if (s.getType().equalsIgnoreCase(type)) {
                    filtered.add(s);
                }
            }
            return Response.ok(filtered).build();
        }

        return Response.ok(sensorList).build();
    }

    // POST /api/v1/sensors — register a new sensor
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSensor(Sensor sensor) {

        // Validate name
        if (sensor.getType() == null || sensor.getType().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createError("Sensor type is required"))
                    .build();
        }

        // Validate roomId exists
        if (sensor.getRoomId() == null || sensor.getRoomId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createError("roomId is required"))
                    .build();
        }

        // Check if the room actually exists — 422 if not
        if (!DataStore.rooms.containsKey(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                    "Room with ID '" + sensor.getRoomId() +
                            "' does not exist. Cannot register sensor."
            );
        }

        // Generate ID if not provided
        if (sensor.getId() == null || sensor.getId().isEmpty()) {
            sensor.setId(UUID.randomUUID().toString());
        }

        // Set default status if not provided
        if (sensor.getStatus() == null || sensor.getStatus().isEmpty()) {
            sensor.setStatus("ACTIVE");
        }

        // Save sensor
        DataStore.sensors.put(sensor.getId(), sensor);

        // Add sensor ID to the room's sensorIds list
        DataStore.rooms.get(sensor.getRoomId())
                .getSensorIds()
                .add(sensor.getId());

        return Response.created(URI.create("/api/v1/sensors/" + sensor.getId()))
                .entity(sensor)
                .build();
    }

    // GET /api/v1/sensors/{sensorId} — get a specific sensor
    @GET
    @Path("/{sensorId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = DataStore.sensors.get(sensorId);

        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createError("Sensor not found with ID: " + sensorId))
                    .build();
        }

        return Response.ok(sensor).build();
    }

    // Sub-resource locator — delegates to SensorReadingResource
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(
            @PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }

    // Helper method
    private Map<String, String> createError(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}