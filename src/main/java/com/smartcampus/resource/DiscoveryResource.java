package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {
        Map<String, Object> info = new HashMap<>();
        info.put("version", "1.0");
        info.put("name", "Smart Campus API");
        info.put("description", "REST API for managing campus rooms and sensors");
        info.put("contact", "admin@smartcampus.ac.uk");

        Map<String, String> resources = new HashMap<>();
        resources.put("rooms", "/api/v1/rooms");
        resources.put("sensors", "/api/v1/sensors");
        info.put("resources", resources);

        return Response.ok(info).build();
    }
}