package com.smartcampus.resource;

import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
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

public class SensorReadingResource {

    private final String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    // GET /api/v1/sensors/{sensorId}/readings — get all readings
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReadings() {
        // Check sensor exists
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createError("Sensor not found with ID: " + sensorId))
                    .build();
        }

        List<SensorReading> history = DataStore.readings
                .getOrDefault(sensorId, new ArrayList<>());

        return Response.ok(history).build();
    }

    // POST /api/v1/sensors/{sensorId}/readings — add a new reading
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading reading) {
        // Check sensor exists
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createError("Sensor not found with ID: " + sensorId))
                    .build();
        }

        // 403 if sensor is under MAINTENANCE
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                    "Sensor '" + sensorId +
                            "' is currently under MAINTENANCE and cannot accept new readings."
            );
        }

        // 403 if sensor is OFFLINE
        if ("OFFLINE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                    "Sensor '" + sensorId +
                            "' is OFFLINE and cannot accept new readings."
            );
        }

        // Generate ID and timestamp
        reading.setId(UUID.randomUUID().toString());
        reading.setTimestamp(System.currentTimeMillis());

        // Save reading
        DataStore.readings
                .computeIfAbsent(sensorId, k -> new ArrayList<>())
                .add(reading);

        // ⭐ Side effect — update parent sensor's currentValue
        sensor.setCurrentValue(reading.getValue());

        return Response.created(
                        URI.create("/api/v1/sensors/" + sensorId + "/readings/" + reading.getId()))
                .entity(reading)
                .build();
    }

    // Helper method
    private Map<String, String> createError(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}