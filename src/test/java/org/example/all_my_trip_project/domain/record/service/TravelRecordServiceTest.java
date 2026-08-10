package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.CreateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordAccessView;
import org.example.all_my_trip_project.domain.record.dto.UpdateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code TravelRecordService}는 트랜잭션 경계와 협력 객체 호출 순서만 조율한다. 각 협력 클래스의
 * 실제 비즈니스 로직은 해당 클래스의 단위 테스트가 검증하므로, 이 테스트는 "누구를 어떤 순서로
 * 부르는가"와 userId 검증 가드만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelRecordServiceTest {

    @Mock
    private ActiveMemberGuard activeMemberGuard;
    @Mock
    private TravelRecordValidator validator;
    @Mock
    private TravelRecordCreator creator;
    @Mock
    private TravelRecordReader reader;
    @Mock
    private TravelRecordModifier modifier;
    @Mock
    private TravelRecordRemover remover;
    @Mock
    private TravelRecordImageReplacer imageReplacer;
    @Mock
    private TravelRecordResponseMapper responseMapper;

    private TravelRecordService travelRecordService;

    @BeforeEach
    void setUp() {
        travelRecordService = new TravelRecordService(
                activeMemberGuard, validator, creator, reader, modifier, remover, imageReplacer, responseMapper);
    }

    @Test
    void createChecksActiveMembershipBeforeCreating() {
        TravelRecordEntity created = record();
        when(creator.create(anyLong(), any())).thenReturn(created);

        travelRecordService.create(42L, createRequest());

        InOrder order = inOrder(activeMemberGuard, creator, responseMapper);
        order.verify(activeMemberGuard).requireActiveMember(42L);
        order.verify(creator).create(42L, createRequest());
        order.verify(responseMapper).toResponse(created, List.of());
    }

    @Test
    void createRejectsUnauthenticatedUser() {
        assertThatThrownBy(() -> travelRecordService.create(null, createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(creator, never()).create(any(), any());
    }

    @Test
    void getMyRecordsRejectsInvalidUserId() {
        assertThatThrownBy(() -> travelRecordService.getMyRecords(0L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(reader, never()).getMyRecords(any());
    }

    @Test
    void updateLoadsOwnedRecordBeforeModifying() {
        TravelRecordEntity owned = record();
        when(reader.findOwned(42L, 1L)).thenReturn(owned);
        when(reader.findImages(1L)).thenReturn(List.of());
        UpdateTravelRecordRequest request =
                new UpdateTravelRecordRequest("제목", "본문", (short) 4, RecordVisibility.PRIVATE);

        travelRecordService.update(42L, 1L, request);

        InOrder order = inOrder(reader, modifier, responseMapper);
        order.verify(reader).findOwned(42L, 1L);
        order.verify(modifier).update(owned, request);
        order.verify(responseMapper).toResponse(owned, List.of());
    }

    @Test
    void replaceImagesValidatesAfterConfirmingOwnershipThenReplaces() {
        TravelRecordEntity owned = record();
        when(reader.findOwned(42L, 1L)).thenReturn(owned);
        when(reader.findImages(1L)).thenReturn(List.of());
        ReplaceRecordImagesRequest request = new ReplaceRecordImagesRequest(List.of());

        travelRecordService.replaceImages(42L, 1L, request);

        InOrder order = inOrder(reader, validator, imageReplacer);
        order.verify(reader).findOwned(42L, 1L);
        order.verify(validator).validateImages(request);
        order.verify(imageReplacer).replace(1L, request);
    }

    @Test
    void deleteLoadsOwnedRecordBeforeRemoving() {
        TravelRecordEntity owned = record();
        when(reader.findOwned(42L, 1L)).thenReturn(owned);

        travelRecordService.delete(42L, 1L);

        InOrder order = inOrder(reader, remover);
        order.verify(reader).findOwned(42L, 1L);
        order.verify(remover).remove(owned);
    }

    @Test
    void deleteRejectsUnauthenticatedUser() {
        assertThatThrownBy(() -> travelRecordService.delete(null, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(reader, never()).findOwned(any(), any());
    }

    @Test
    void requireAccessibleRecordDelegatesToReader() {
        TravelRecordAccessView view =
                new TravelRecordAccessView(1L, 10L, 42L, RecordVisibility.PUBLIC);
        when(reader.getAccessView(7L, 1L)).thenReturn(view);

        assertThat(travelRecordService.requireAccessibleRecord(7L, 1L)).isSameAs(view);
    }

    private TravelRecordEntity record() {
        return TravelRecordEntity.create(10L, 42L, "제목", "본문", (short) 5, RecordVisibility.PUBLIC);
    }

    private CreateTravelRecordRequest createRequest() {
        return new CreateTravelRecordRequest(10L, "제목", "본문", (short) 5, RecordVisibility.PUBLIC);
    }
}
