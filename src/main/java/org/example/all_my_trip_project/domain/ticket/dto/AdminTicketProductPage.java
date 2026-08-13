package org.example.all_my_trip_project.domain.ticket.dto;

import java.util.List;

public record AdminTicketProductPage(
        List<AdminTicketProductDTO> items,
        int page,
        int size,
        long total,
        int totalPages
) {}
