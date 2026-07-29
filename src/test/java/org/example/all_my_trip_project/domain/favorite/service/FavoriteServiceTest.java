package org.example.all_my_trip_project.domain.favorite.service;

import org.example.all_my_trip_project.domain.favorite.dao.FavoriteDAO;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteResult;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {
    @Mock
    private FavoriteDAO favoriteDAO;
    @Mock
    private PlaceDAO placeDAO;
    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void addStoresAndReturnsFavorite() {
        PlaceDTO place = PlaceDTO.builder().placeId(100L).build();
        FavoriteResult favorite = new FavoriteResult(
                1L, 1L, 100L, "다시 가기", OffsetDateTime.now(),
                "서울 관광지", "ATTRACTION", "서울", "https://example.com/image.jpg");
        when(placeDAO.findById(100L)).thenReturn(Optional.of(place));
        when(favoriteDAO.find(1L, 100L)).thenReturn(Optional.of(favorite));

        FavoriteResult result = favoriteService.add(1L, 100L, "  다시 가기  ");

        assertThat(result).isSameAs(favorite);
        verify(favoriteDAO).insert(1L, 100L, "다시 가기");
    }

    @Test
    void getFavoritesAppliesPagination() {
        when(favoriteDAO.findByUserId(1L, 20, 20)).thenReturn(List.of());

        assertThat(favoriteService.getFavorites(1L, 1, 20)).isEmpty();

        verify(favoriteDAO).findByUserId(1L, 20, 20);
    }

    @Test
    void addRejectsMissingPlace() {
        when(placeDAO.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.add(1L, 999L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("즐겨찾기할 장소를 찾을 수 없습니다. placeId=999");
    }

    @Test
    void removeIsSafeWhenFavoriteDoesNotExist() {
        favoriteService.remove(1L, 100L);

        verify(favoriteDAO).delete(1L, 100L);
    }
}
