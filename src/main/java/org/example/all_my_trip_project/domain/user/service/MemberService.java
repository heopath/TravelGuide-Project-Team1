package org.example.all_my_trip_project.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.user.dto.MemberResponse;
import org.example.all_my_trip_project.domain.user.dto.UpdateMemberProfileRequest;
import org.example.all_my_trip_project.domain.user.dto.UpdatePreferencesRequest;
import org.example.all_my_trip_project.domain.user.dto.UserPreferenceResponse;
import org.example.all_my_trip_project.domain.user.entity.UserPreferenceEntity;
import org.example.all_my_trip_project.domain.user.entity.UserEntity;
import org.example.all_my_trip_project.domain.user.repository.UserPreferenceRepository;
import org.example.all_my_trip_project.domain.user.repository.UserPreferenceView;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService implements ActiveMemberGuard {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public UserPreferenceResponse getPreferences(Long userId) {
        validateMember(userId);
        return loadPreferences(userId);
    }

    @Override
    public void requireActiveMember(Long userId) {
        validateMember(userId);
    }

    @Transactional
    public MemberResponse updateProfile(
            Long userId,
            UpdateMemberProfileRequest request
    ) {
        UserEntity user = validateMember(userId);
        String nickname = request.nickname().trim();
        if (nickname.length() < 2 || nickname.length() > 20) {
            throw new IllegalArgumentException("닉네임은 2자 이상 20자 이하여야 합니다.");
        }

        if (userRepository
                .existsByNicknameAndUserIdNotAndDeletedAtIsNull(
                        nickname,
                        userId
                )) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        user.updateNickname(nickname);

        return new MemberResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getStatus()
        );
    }

    @Transactional
    public UserPreferenceResponse replacePreferences(
            Long userId,
            UpdatePreferencesRequest request
    ) {
        validateMember(userId);
        validateNoDuplicateStyles(request.preferences());
        validateActiveStyles(request.preferences());

        List<UserPreferenceEntity> existingPreferences =
                userPreferenceRepository.findAllByUserId(userId);

        Map<Short, UserPreferenceEntity> existingByStyle =
                existingPreferences.stream()
                        .collect(Collectors.toMap(
                                UserPreferenceEntity::getTravelStyleId,
                                Function.identity()
                        ));

        Set<Short> requestedStyleIds = request.preferences().stream()
                .map(UpdatePreferencesRequest.PreferenceItem::travelStyleId)
                .collect(Collectors.toSet());

        List<UserPreferenceEntity> removedExplicitPreferences =
                existingPreferences.stream()
                        .filter(UserPreferenceEntity::isExplicit)
                        .filter(preference -> !requestedStyleIds.contains(
                                preference.getTravelStyleId()
                        ))
                        .toList();

        userPreferenceRepository.deleteAll(removedExplicitPreferences);

        List<UserPreferenceEntity> savedPreferences = request.preferences()
                .stream()
                .map(item -> {
                    UserPreferenceEntity preference = existingByStyle.get(
                            item.travelStyleId()
                    );

                    if (preference == null) {
                        return UserPreferenceEntity.explicit(
                                userId,
                                item.travelStyleId(),
                                item.preferenceScore()
                        );
                    }

                    preference.replaceWithExplicitScore(
                            item.preferenceScore()
                    );
                    return preference;
                })
                .toList();

        userPreferenceRepository.saveAllAndFlush(savedPreferences);
        return loadPreferences(userId);
    }

    private UserEntity validateMember(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED
                ));

        if ("SUSPENDED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        if ("WITHDRAWN".equals(user.getStatus())
                || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        return user;
    }

    private void validateNoDuplicateStyles(
            List<UpdatePreferencesRequest.PreferenceItem> preferences
    ) {
        Set<Short> uniqueStyleIds = new HashSet<>();

        boolean duplicated = preferences.stream()
                .map(UpdatePreferencesRequest.PreferenceItem::travelStyleId)
                .anyMatch(styleId -> !uniqueStyleIds.add(styleId));

        if (duplicated) {
            throw new BusinessException(
                    ErrorCode.TRAVEL_STYLE_DUPLICATED
            );
        }
    }

    private void validateActiveStyles(
            List<UpdatePreferencesRequest.PreferenceItem> preferences
    ) {
        if (preferences.isEmpty()) {
            return;
        }

        Set<Short> requestedStyleIds = preferences.stream()
                .map(UpdatePreferencesRequest.PreferenceItem::travelStyleId)
                .collect(Collectors.toSet());

        Set<Short> activeStyleIds = new HashSet<>(
                userPreferenceRepository.findActiveTravelStyleIds(
                        requestedStyleIds
                )
        );

        if (!activeStyleIds.containsAll(requestedStyleIds)) {
            throw new BusinessException(
                    ErrorCode.TRAVEL_STYLE_NOT_FOUND
            );
        }
    }

    private UserPreferenceResponse loadPreferences(Long userId) {
        List<UserPreferenceResponse.PreferenceItem> preferences =
                userPreferenceRepository.findViewsByUserId(userId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return new UserPreferenceResponse(preferences);
    }

    private UserPreferenceResponse.PreferenceItem toResponse(
            UserPreferenceView view
    ) {
        return new UserPreferenceResponse.PreferenceItem(
                view.getTravelStyleId(),
                view.getCode(),
                view.getName(),
                view.getPreferenceScore(),
                view.getSource()
        );
    }
}
