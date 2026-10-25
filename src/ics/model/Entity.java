package ics.model;

/**
 * The parent of every record we save: accounts, policies, claims and payouts.
 *
 * WHY: every record needs an ID and a way to become one line of a txt file. Writing that here once
 * means one Repository class can store ANY kind of record (see repository/Repository.java).
 *
 * "abstract" = you never make a plain Entity, only one of its children.
 */
public abstract class Entity {

    private String id;   // for example "CLM-0001"; empty until the record is saved for the first time

    protected Entity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** The Repository gives a new record its ID when it is first saved. An ID can be set only once. */
    public void setId(String id) {
        if (this.id != null && !this.id.isBlank()) {
            throw new IllegalStateException("The ID is already " + this.id);
        }
        this.id = id;
    }

    /** Turns this record into one line of its txt file. Each child decides its own columns. */
    public abstract String toFileLine();
}
