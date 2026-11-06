package ics.model;

public abstract class Entity {
    private String id;

    protected Entity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (this.id != null && !this.id.isBlank()) {
            throw new IllegalStateException("The ID is already " + this.id);
        }
        this.id = id;
    }

    public abstract String toFileLine();
}
