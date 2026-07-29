package org.example.all_my_trip_project.domain.place.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceImageResult implements Serializable {
    private Long placeImageId;
    private String imageUrl;
    private String altText;
    private Integer sortOrder;
    private Boolean primaryImage;
}
