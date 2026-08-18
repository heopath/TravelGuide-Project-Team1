package org.example.all_my_trip_project.domain.ticket.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;
import org.example.all_my_trip_project.domain.ticket.mapper.AdminTicketMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class AdminTicketDAO {

    private final AdminTicketMapper adminTicketMapper;

    public List<AdminTicketProductDTO> findAdminPage(String keyword, String status, int offset, int size) {
        return adminTicketMapper.findAdminPage(keyword, status, offset, size);
    }

    public long countAdmin(String keyword, String status) {
        return adminTicketMapper.countAdmin(keyword, status);
    }

    public Optional<AdminTicketProductDTO> findAdminById(Long ticketProductId) {
        return adminTicketMapper.findAdminById(ticketProductId);
    }

    public int updateStatus(Long ticketProductId, String status) {
        return adminTicketMapper.updateStatus(ticketProductId, status);
    }

    public Long insertProduct(AdminTicketProductRequest request) {
        return adminTicketMapper.insertProduct(request);
    }

    public int updateProduct(Long ticketProductId, AdminTicketProductRequest request) {
        return adminTicketMapper.updateProduct(ticketProductId, request);
    }

    public boolean existsPlace(Long placeId) {
        return adminTicketMapper.existsPlace(placeId);
    }

    public List<AdminTicketSlotDTO> findSlots(Long ticketProductId) {
        return adminTicketMapper.findSlots(ticketProductId);
    }

    public Optional<AdminTicketSlotDTO> findSlotForUpdate(Long slotId) {
        return adminTicketMapper.findSlotForUpdate(slotId);
    }

    public int updateInventory(Long slotId, int totalQuantity) {
        return adminTicketMapper.updateInventory(slotId, totalQuantity);
    }

    public List<AdminTicketOptionDTO> findOptions(Long ticketProductId) {
        return adminTicketMapper.findOptions(ticketProductId);
    }

    public Optional<AdminTicketOptionDTO> findOptionById(Long optionId) {
        return adminTicketMapper.findOptionById(optionId);
    }

    public Long insertOption(Long ticketProductId, AdminTicketOptionRequest request) {
        return adminTicketMapper.insertOption(ticketProductId, request);
    }

    public int updateOption(Long optionId, AdminTicketOptionRequest request) {
        return adminTicketMapper.updateOption(optionId, request);
    }

    public boolean existsConflictingOption(Long ticketProductId, String name, int sortOrder, Long optionId) {
        return adminTicketMapper.existsConflictingOption(ticketProductId, name, sortOrder, optionId);
    }

    public Long insertSlot(Long optionId, LocalDate usageDate, LocalTime startTime, LocalTime endTime) {
        return adminTicketMapper.insertSlot(optionId, usageDate, startTime, endTime);
    }

    public int insertInventory(Long slotId, int totalQuantity) {
        return adminTicketMapper.insertInventory(slotId, totalQuantity);
    }

    public boolean existsSlot(Long optionId, LocalDate usageDate, LocalTime startTime) {
        return adminTicketMapper.existsSlot(optionId, usageDate, startTime);
    }

    public int updateSlotStatus(Long slotId, String status) {
        return adminTicketMapper.updateSlotStatus(slotId, status);
    }

    public int countSlotReserved(Long slotId) {
        return adminTicketMapper.countSlotReserved(slotId);
    }

    public Optional<Long> findProductIdBySlot(Long slotId) {
        return adminTicketMapper.findProductIdBySlot(slotId);
    }

    public Optional<Long> findProductIdByOption(Long optionId) {
        return adminTicketMapper.findProductIdByOption(optionId);
    }
}
