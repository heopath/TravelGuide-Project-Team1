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
public class PlaceStyleResult implements Serializable {
    private Long travelStyleId;
    private String code;
    private String name;
    private Integer relevanceScore;
}
