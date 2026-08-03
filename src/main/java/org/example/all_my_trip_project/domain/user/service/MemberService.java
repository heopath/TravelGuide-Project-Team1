package org.example.all_my_trip_project.domain.user.service;

import lombok.RequiredArgsConstructor;
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
public class MemberService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public UserPreferenceResponse getPreferences(Long userId) {
        validateMember(userId);
        return loadPreferences(userId);
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

    private void validateMember(Long userId) {
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
