package org.example.all_my_trip_project.domain.user.service;

import org.example.all_my_trip_project.domain.user.dto.UpdatePreferencesRequest;
import org.example.all_my_trip_project.domain.user.dto.UpdateMemberProfileRequest;
import org.example.all_my_trip_project.domain.user.entity.UserEntity;
import org.example.all_my_trip_project.domain.user.entity.UserPreferenceEntity;
import org.example.all_my_trip_project.domain.user.repository.UserPreferenceRepository;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void updateProfileChangesNickname() {
        UserEntity user = activeUser();
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(userRepository
                .existsByNicknameAndUserIdNotAndDeletedAtIsNull(
                        "새여행자",
                        11L
                ))
                .thenReturn(false);

        var result = memberService.updateProfile(
                11L,
                new UpdateMemberProfileRequest("  새여행자  ")
        );

        assertThat(result.nickname()).isEqualTo("새여행자");
        assertThat(user.getNickname()).isEqualTo("새여행자");
    }

    @Test
    void updateProfileRejectsDuplicatedNickname() {
        when(userRepository.findById(11L))
                .thenReturn(Optional.of(activeUser()));
        when(userRepository
                .existsByNicknameAndUserIdNotAndDeletedAtIsNull(
                        "중복닉네임",
                        11L
                ))
                .thenReturn(true);

        assertThatThrownBy(() -> memberService.updateProfile(
                11L,
                new UpdateMemberProfileRequest("중복닉네임")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    void updateProfileValidatesNicknameAfterTrimming() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> memberService.updateProfile(
                11L, new UpdateMemberProfileRequest(" a ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("닉네임은 2자 이상 20자 이하여야 합니다.");

        verify(userRepository, never())
                .existsByNicknameAndUserIdNotAndDeletedAtIsNull("a", 11L);
    }

    @Test
    void replacePreferencesUpdatesAddsAndRemovesExplicitPreferences() {
        UserPreferenceEntity sightseeing =
                UserPreferenceEntity.explicit(11L, (short) 1, (short) 50);
        UserPreferenceEntity food =
                UserPreferenceEntity.explicit(11L, (short) 2, (short) 60);

        UpdatePreferencesRequest request = request(
                item(1, 90),
                item(3, 80)
        );

        when(userRepository.findById(11L)).thenReturn(Optional.of(activeUser()));
        when(userPreferenceRepository.findActiveTravelStyleIds(
                anyCollection()
        )).thenReturn(List.of((short) 1, (short) 3));
        when(userPreferenceRepository.findAllByUserId(11L))
                .thenReturn(List.of(sightseeing, food));
        when(userPreferenceRepository.findViewsByUserId(11L))
                .thenReturn(List.of());

        memberService.replacePreferences(11L, request);

        verify(userPreferenceRepository).deleteAll(List.of(food));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserPreferenceEntity>> savedCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(userPreferenceRepository)
                .saveAllAndFlush(savedCaptor.capture());

        assertThat(savedCaptor.getValue())
                .extracting(
                        UserPreferenceEntity::getTravelStyleId,
                        UserPreferenceEntity::getPreferenceScore,
                        UserPreferenceEntity::getSource
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                (short) 1,
                                (short) 90,
                                "EXPLICIT"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                (short) 3,
                                (short) 80,
                                "EXPLICIT"
                        )
                );
    }

    @Test
    void replacePreferencesRejectsDuplicateStyleIds() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(activeUser()));

        UpdatePreferencesRequest request = request(
                item(1, 90),
                item(1, 70)
        );

        assertThatThrownBy(
                () -> memberService.replacePreferences(11L, request)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRAVEL_STYLE_DUPLICATED);

        verify(userPreferenceRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    void requireActiveMemberPassesForActiveMember() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(activeUser()));

        assertThatCode(() -> memberService.requireActiveMember(11L))
                .doesNotThrowAnyException();
    }

    @Test
    void requireActiveMemberRejectsUnknownMember() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.requireActiveMember(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void requireActiveMemberRejectsSuspendedMember() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(userWithStatus("SUSPENDED")));

        assertThatThrownBy(() -> memberService.requireActiveMember(11L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    void requireActiveMemberRejectsWithdrawnMember() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(userWithStatus("WITHDRAWN")));

        assertThatThrownBy(() -> memberService.requireActiveMember(11L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
    }

    private UserEntity userWithStatus(String status) {
        UserEntity user = activeUser();
        try {
            java.lang.reflect.Field field = UserEntity.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(user, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }

    @Test
    void replacePreferencesRejectsInactiveStyle() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(activeUser()));
        when(userPreferenceRepository.findActiveTravelStyleIds(
                anyCollection()
        )).thenReturn(List.of());

        UpdatePreferencesRequest request = request(item(99, 70));

        assertThatThrownBy(
                () -> memberService.replacePreferences(11L, request)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRAVEL_STYLE_NOT_FOUND);
    }

    private UpdatePreferencesRequest request(
            UpdatePreferencesRequest.PreferenceItem... items
    ) {
        return new UpdatePreferencesRequest(List.of(items));
    }

    private UpdatePreferencesRequest.PreferenceItem item(
            int travelStyleId,
            int score
    ) {
        return new UpdatePreferencesRequest.PreferenceItem(
                (short) travelStyleId,
                (short) score
        );
    }

    private UserEntity activeUser() {
        return UserEntity.create(
                "member@example.com",
                "encoded-password",
                "여행자"
        );
    }
}
