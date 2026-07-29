package org.example.all_my_trip_project.domain.place.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
@AllArgsConstructor
public class PlaceDetailResult implements Serializable {
    private final PlaceDTO place;
    private final List<PlaceImageResult> images;
    private final List<PlaceStyleResult> styles;
}
