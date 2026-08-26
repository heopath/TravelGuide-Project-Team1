package org.example.all_my_trip_project.domain.record.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "travel-record.local-storage")
public class TravelRecordLocalStorageProperties {

    private boolean enabled;
    private String directory = System.getProperty("java.io.tmpdir") + "/all-my-trips/travel-records";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
}
