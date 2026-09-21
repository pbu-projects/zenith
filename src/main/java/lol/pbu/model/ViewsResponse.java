package lol.pbu.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public class ViewsResponse {
    private List<View> views;

    public List<View> getViews() {
        return views;
    }

    public void setViews(List<View> views) {
        this.views = views;
    }

    @Serdeable
    public static class View {
        private Long id;
        private String title;
        private String description;
        private Boolean active;
        private Integer position;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public Integer getPosition() { return position; }
        public void setPosition(Integer position) { this.position = position; }
    }
}
